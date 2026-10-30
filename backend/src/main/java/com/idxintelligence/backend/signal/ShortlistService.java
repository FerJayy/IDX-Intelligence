package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.Reason;
import com.idxintelligence.backend.broker.BrokerAnalysisService;
import com.idxintelligence.backend.broker.BrokerDailyActivity;
import com.idxintelligence.backend.broker.BrokerPosition;
import com.idxintelligence.backend.broker.DailyBrokerFlow;
import com.idxintelligence.backend.broker.FlowAssessment;
import com.idxintelligence.backend.market.TodayPriceSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * "Worth a look": the tracked stocks checked against eight conditions that professional practice and
 * research point to, ranked by how many they pass:
 * trend (price > 50-day > rising 200-day average; within 25% of the 52-week high — the "trend template"),
 * strength (beat IHSG over 3 months — the momentum effect documented since Jegadeesh & Titman 1993),
 * big money (big brokers collecting over 10 days and a month; foreigners net buying — bandarmologi) and
 * risk (at most 10% above what the main buyers paid; liquid; not HSC or already chasing). A research shortlist, not advice: it also replays itself on past days (only data known that
 * evening, entry next open, +1/+3/+5 days, 0,4% cost, vs IHSG) so its usefulness is measured, not assumed.
 */
@Service
public class ShortlistService {

    public static final int SIZE = 10;
    public static final int CHECKS = 8;
    static final int BROKER_DAYS = 10;
    /** Same tolerance as the summary's "far above buyers' cost" warning. */
    static final double MAX_ABOVE_COST = 0.10;
    /** Enough calendar days before the first broker day for 200-day averages and 52-week highs. */
    private static final int PRICE_LEAD_DAYS = 400;
    static final int MONTHS3_BARS = 63;
    static final int YEAR_BARS = 250;

    private final JdbcClient jdbc;
    private final RadarService radar;
    private final SignalDataRepository prices;
    private final Map<String, Shortlist> cache = new ConcurrentHashMap<>();

    public ShortlistService(JdbcClient jdbc, RadarService radar, SignalDataRepository prices) {
        this.jdbc = jdbc;
        this.radar = radar;
        this.prices = prices;
    }

    /**
     * @param checks   one Reason per check (code SL_*); points 1 = passed, 0 = not passed
     * @param eligible passes the hard filters (liquid, not HSC/chasing): only these can top the list, as
     *                 professionals don't consider stocks they couldn't trade sensibly whatever else they show
     */
    public record Candidate(String ticker, String name, boolean hsc, int passed, boolean eligible, BigDecimal lastPrice,
                            Double changePercent, List<Reason> checks) {

        /** The same candidate showing today's price; the checks stay on the stored end-of-day data. */
        Candidate withPrice(BigDecimal price, Double change) {
            return new Candidate(ticker, name, hsc, passed, eligible, price, change, checks);
        }
    }

    /** Hard filters first, then most checks passed, then strongest against IHSG. */
    static final Comparator<Candidate> RANKING = Comparator.comparing((Candidate c) -> !c.eligible())
            .thenComparing(Comparator.comparingInt(Candidate::passed).reversed())
            .thenComparing(c -> -strength(c.checks()))
            .thenComparing(Candidate::ticker);

    static boolean eligible(List<Reason> checks) {
        return checks.stream().filter(c -> c.code().equals("SL_LIQUID") || c.code().equals("SL_SAFE"))
                .allMatch(c -> c.points() != null && c.points() > 0);
    }

    private static double strength(List<Reason> checks) {
        return checks.stream().filter(c -> c.code().equals("SL_STRENGTH")).findFirst()
                .map(c -> c.values().get("excess3m")).filter(Number.class::isInstance)
                .map(v -> ((Number) v).doubleValue()).orElse(Double.NEGATIVE_INFINITY);
    }

    /**
     * @param candidates every tracked stock with enough history, most checks passed first
     * @param record     the shortlist replayed on past broker days
     * @param pricesAsOf set when the shown prices are today's (delayed) rather than the stored close
     */
    public record Shortlist(OffsetDateTime generatedAt, LocalDate priceDate, LocalDate brokerDate, int checks,
                            int size, List<Candidate> candidates, RadarTrackRecordService.TrackRecord record,
                            OffsetDateTime pricesAsOf) {

        /** Prices only: the ranking and checks are end-of-day by design (broker data arrives in the evening). */
        public Shortlist withTodayPrices(TodayPriceSource.TodayPrices today) {
            if (priceDate != null && !priceDate.isBefore(today.asOf().toLocalDate())) return this;
            List<Candidate> shown = candidates.stream().map(c -> {
                TodayPriceSource.TodayPrice p = today.prices().get(c.ticker());
                if (p == null || p.price() == null) return c;
                Double change = p.previousClose() == null || p.previousClose().signum() <= 0 ? null
                        : (p.price().doubleValue() / p.previousClose().doubleValue() - 1) * 100;
                return c.withPrice(p.price(), change);
            }).toList();
            return new Shortlist(generatedAt, priceDate, brokerDate, checks, size, shown, record, today.asOf());
        }
    }

    record Pick(LocalDate date, Candidate candidate, double[] net, double[] excess) {
        int passed() {
            return candidate.passed();
        }
    }

    public Shortlist shortlist() {
        String version = jdbc.sql("""
                SELECT coalesce((SELECT max(fetched_at)::text FROM broker_import_day), '') || '|' ||
                       coalesce((SELECT max(trade_date)::text FROM daily_price), '') || '|' ||
                       (SELECT count(*) FROM stock WHERE broker_tracked AND active)
                """).query(String.class).single();
        cache.keySet().removeIf(k -> !k.equals(version));
        return cache.computeIfAbsent(version, v -> load());
    }

    private Shortlist load() {
        LocalDate firstBrokerDay = jdbc.sql("SELECT min(trade_date) FROM broker_daily_activity").query(LocalDate.class)
                .optional().orElse(null);
        Map<String, String> names = new LinkedHashMap<>();
        Set<String> hsc = new HashSet<>();
        jdbc.sql("""
                SELECT s.ticker, s.name, (h.ticker IS NOT NULL) AS hsc
                FROM stock s LEFT JOIN hsc_stock h ON h.ticker = s.ticker
                WHERE s.broker_tracked AND s.active ORDER BY s.ticker
                """)
                .query((rs, n) -> {
                    names.put(rs.getString("ticker"), rs.getString("name"));
                    if (rs.getBoolean("hsc")) hsc.add(rs.getString("ticker"));
                    return null;
                })
                .list();
        if (firstBrokerDay == null || names.isEmpty()) {
            return new Shortlist(OffsetDateTime.now(BacktestService.JAKARTA), null, null, CHECKS, SIZE, List.of(), null, null);
        }
        Map<String, List<BrokerDailyActivity>> activity = radar.activity(firstBrokerDay);
        Map<String, List<Bar>> bars = prices.adjustedBars(firstBrokerDay.minusDays(PRICE_LEAD_DAYS), true);
        Map<LocalDate, double[]> index = prices.compositeIndex(firstBrokerDay.minusDays(PRICE_LEAD_DAYS));
        return compute(names, hsc, activity, bars, index);
    }

    static Shortlist compute(Map<String, String> names, Set<String> hsc, Map<String, List<BrokerDailyActivity>> activity,
                             Map<String, List<Bar>> barsByTicker, Map<LocalDate, double[]> index) {
        List<Candidate> today = new ArrayList<>();
        List<Pick> picks = new ArrayList<>();
        LocalDate priceDate = null;
        LocalDate brokerDate = null;

        for (var entry : names.entrySet()) {
            String ticker = entry.getKey();
            List<Bar> bars = barsByTicker.getOrDefault(ticker, List.of());
            List<BrokerDailyActivity> rows = activity.getOrDefault(ticker, List.of()).stream()
                    .sorted(Comparator.comparing(BrokerDailyActivity::date)).toList();
            if (bars.isEmpty() || rows.isEmpty()) continue;
            Map<LocalDate, Integer> barIndex = new HashMap<>();
            for (int i = 0; i < bars.size(); i++) barIndex.put(bars.get(i).date(), i);

            // Every broker day: the checks with only what was known that evening.
            List<LocalDate> days = rows.stream().map(BrokerDailyActivity::date).distinct().toList();
            int rowEnd = 0;
            for (int d = 0; d < days.size(); d++) {
                LocalDate day = days.get(d);
                while (rowEnd < rows.size() && !rows.get(rowEnd).date().isAfter(day)) rowEnd++;
                Integer i = barIndex.get(day);
                if (d + 1 < BROKER_DAYS || i == null) continue;
                List<Reason> checks = checks(rows.subList(0, rowEnd), bars.subList(0, i + 1), index, day, hsc.contains(ticker));
                if (checks == null) continue;
                int passed = (int) checks.stream().filter(c -> c.points() != null && c.points() > 0).count();
                if (i + 1 < bars.size()) {
                    Candidate then = new Candidate(ticker, entry.getValue(), hsc.contains(ticker), passed, eligible(checks),
                            null, null, checks);
                    picks.add(new Pick(day, then, RadarTrackRecordService.outcome(bars, i, index, false),
                            RadarTrackRecordService.outcome(bars, i, index, true)));
                }
            }

            // Today's candidate: the latest prices and broker days.
            List<Reason> checks = checks(rows, bars, index, days.getLast(), hsc.contains(ticker));
            if (checks == null) continue;
            int passed = (int) checks.stream().filter(c -> c.points() != null && c.points() > 0).count();
            Bar last = bars.getLast();
            Double change = bars.size() < 2 ? null : last.close() / bars.get(bars.size() - 2).close() * 100 - 100;
            today.add(new Candidate(ticker, entry.getValue(), hsc.contains(ticker), passed, eligible(checks),
                    BigDecimal.valueOf(last.close()).setScale(0, RoundingMode.HALF_UP), change, checks));
            priceDate = priceDate == null || last.date().isAfter(priceDate) ? last.date() : priceDate;
            brokerDate = brokerDate == null || days.getLast().isAfter(brokerDate) ? days.getLast() : brokerDate;
        }

        today.sort(RANKING);
        return new Shortlist(OffsetDateTime.now(BacktestService.JAKARTA), priceDate, brokerDate, CHECKS, SIZE, today,
                trackRecord(picks), null);
    }

    /** The eight checks for one stock on one day (bars end on that day), or null without enough price history. */
    static List<Reason> checks(List<BrokerDailyActivity> rows, List<Bar> bars, Map<LocalDate, double[]> index,
                               LocalDate day, boolean hsc) {
        Features f = Features.at(bars, bars.size() - 1);
        if (f == null) return null;
        int end = bars.size() - 1;
        Double ma50 = average(bars, end, 50);
        Double ma200 = average(bars, end, 200);
        Double ma200Before = average(bars, end - 20, 200);
        boolean longTrend = ma50 != null && ma200 != null && ma200Before != null
                && f.close() > ma50 && ma50 > ma200 && ma200 > ma200Before;
        double yearHigh = bars.subList(Math.max(0, bars.size() - YEAR_BARS), bars.size()).stream()
                .mapToDouble(b -> Double.isNaN(b.high()) ? b.close() : b.high()).max().orElse(Double.NaN);
        boolean fullYear = bars.size() >= 120;
        Double fromHigh = fullYear && yearHigh > 0 ? f.close() / yearHigh - 1 : null;
        Double excess3m = excess(bars, index, MONTHS3_BARS);
        BigDecimal last = BigDecimal.valueOf(f.close()).setScale(0, RoundingMode.HALF_UP);

        List<LocalDate> days = rows.stream().map(BrokerDailyActivity::date).distinct().sorted().toList();
        LocalDate tenFrom = days.get(Math.max(0, days.size() - BROKER_DAYS));
        List<BrokerDailyActivity> ten = rows.stream().filter(r -> !r.date().isBefore(tenFrom)).toList();
        List<BrokerDailyActivity> month = rows.stream().filter(r -> !r.date().isBefore(day.minusMonths(1))).toList();
        FlowAssessment.Status tenStatus = status(ten, last);
        FlowAssessment.Status monthStatus = status(month, last);

        List<DailyBrokerFlow> monthFlows = BrokerAnalysisService.dailyFlows(month);
        List<DailyBrokerFlow> foreignKnown = monthFlows.stream().filter(x -> x.foreignNetValue() != null).toList();
        BigDecimal foreign = foreignKnown.size() < 5 ? null
                : foreignKnown.stream().map(DailyBrokerFlow::foreignNetValue).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cost = averageBuyPrice(topBuyers(month));
        Double gap = cost == null || cost.signum() == 0 ? null : f.close() / cost.doubleValue() - 1;

        boolean chasing = f.return5() > MomentumScorer.MAX_RETURN_5D || f.return1() >= MomentumScorer.NEAR_LIMIT_UP;

        List<Reason> checks = new ArrayList<>();
        checks.add(check("SL_LONG_TREND", longTrend, "ma50", ma50 == null ? null : Math.round(ma50),
                "ma200", ma200 == null ? null : Math.round(ma200)));
        checks.add(check("SL_NEAR_HIGH", fromHigh != null && fromHigh >= -0.25, "fromHigh", fromHigh,
                "high", Double.isNaN(yearHigh) ? null : Math.round(yearHigh)));
        checks.add(check("SL_STRENGTH", excess3m != null && excess3m > 0, "excess3m", excess3m));
        checks.add(check("SL_BROKERS", tenStatus == FlowAssessment.Status.ACCUMULATION
                && monthStatus == FlowAssessment.Status.ACCUMULATION, "tenDays", tenStatus.name(), "month", monthStatus.name()));
        checks.add(check("SL_FOREIGN", foreign != null && foreign.signum() > 0, "value", foreign));
        checks.add(check("SL_NOT_STRETCHED", gap != null && gap <= MAX_ABOVE_COST, "gap", gap, "averagePrice", cost));
        checks.add(check("SL_LIQUID", f.avgValue20() >= MomentumScorer.MIN_AVG_VALUE,
                "avgValue", Math.round(f.avgValue20()), "minValue", Math.round(MomentumScorer.MIN_AVG_VALUE)));
        checks.add(check("SL_SAFE", !hsc && !chasing, "hsc", hsc, "change5d", f.return5(), "maxChange5d", MomentumScorer.MAX_RETURN_5D));
        return checks;
    }

    /** Average close of the {@code n} bars ending at {@code end}, or null without enough of them. */
    private static Double average(List<Bar> bars, int end, int n) {
        if (end < n - 1) return null;
        double sum = 0;
        for (int k = end - n + 1; k <= end; k++) sum += bars.get(k).close();
        return sum / n;
    }

    /** Return minus IHSG's over the last {@code n} bars, ending on the latest day both have; null if unknown. */
    private static Double excess(List<Bar> bars, Map<LocalDate, double[]> index, int n) {
        int end = bars.size() - 1;
        while (end >= 0 && end >= bars.size() - 5 && !index.containsKey(bars.get(end).date())) end--;
        if (end < n || !index.containsKey(bars.get(end).date())) return null;
        double[] in = index.get(bars.get(end - n).date());
        double[] out = index.get(bars.get(end).date());
        if (in == null || in[1] <= 0 || bars.get(end - n).close() <= 0) return null;
        return (bars.get(end).close() / bars.get(end - n).close() - 1) - (out[1] / in[1] - 1);
    }

    private static Reason check(String code, boolean pass, Object... values) {
        Object[] withPass = Arrays.copyOf(values, values.length + 2);
        withPass[values.length] = "pass";
        withPass[values.length + 1] = pass;
        return Reason.of(code, pass ? 1 : 0, withPass);
    }

    private static FlowAssessment.Status status(List<BrokerDailyActivity> rows, BigDecimal last) {
        return BrokerAnalysisService.assess(BrokerAnalysisService.dailyFlows(rows), topBuyers(rows), last).status();
    }

    private static List<BrokerPosition> topBuyers(List<BrokerDailyActivity> rows) {
        return BrokerAnalysisService.positions(rows).stream()
                .filter(p -> p.netValue().signum() > 0)
                .sorted(Comparator.comparing(BrokerPosition::netValue).reversed())
                .limit(5)
                .toList();
    }

    private static BigDecimal averageBuyPrice(List<BrokerPosition> buyers) {
        long volume = buyers.stream().mapToLong(BrokerPosition::buyVolume).sum();
        if (volume == 0) return null;
        BigDecimal value = buyers.stream().map(BrokerPosition::buyValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return value.divide(BigDecimal.valueOf(volume), 0, RoundingMode.HALF_UP);
    }

    // ---------- track record ----------

    /** Groups: the daily top 10 (as the page shows), all checks, all but one, and every tracked stock (baseline). */
    static RadarTrackRecordService.TrackRecord trackRecord(List<Pick> picks) {
        TreeMap<LocalDate, List<Pick>> byDate = new TreeMap<>();
        picks.forEach(p -> byDate.computeIfAbsent(p.date(), d -> new ArrayList<>()).add(p));
        Set<Pick> top = new HashSet<>();
        // The daily top 10 exactly as the page would have shown it that evening.
        byDate.values().forEach(day -> day.stream()
                .sorted(Comparator.comparing(Pick::candidate, RANKING))
                .limit(SIZE).forEach(top::add));
        List<RadarTrackRecordService.Group> groups = List.of(
                group("TOP_PICKS", picks, top::contains),
                group("ALL_CHECKS", picks, p -> p.passed() == CHECKS),
                group("ALMOST_ALL", picks, p -> p.passed() >= CHECKS - 1),
                group("ALL", picks, p -> true));
        return new RadarTrackRecordService.TrackRecord(OffsetDateTime.now(BacktestService.JAKARTA), BROKER_DAYS,
                byDate.isEmpty() ? null : byDate.firstKey(), byDate.isEmpty() ? null : byDate.lastKey(), byDate.size(),
                (int) picks.stream().map(p -> p.candidate().ticker()).distinct().count(), BacktestService.ROUND_TRIP_COST, SIZE,
                CHECKS, groups);
    }

    private static RadarTrackRecordService.Group group(String key, List<Pick> all, Predicate<Pick> member) {
        List<Pick> picked = all.stream().filter(member).toList();
        List<RadarTrackRecordService.HorizonStats> horizons = new ArrayList<>();
        for (int h = 0; h < RadarTrackRecordService.HORIZONS.length; h++) {
            int k = h;
            double[] net = picked.stream().mapToDouble(p -> p.net()[k]).filter(v -> !Double.isNaN(v)).toArray();
            double[] excess = picked.stream().mapToDouble(p -> p.excess()[k]).filter(v -> !Double.isNaN(v)).toArray();
            horizons.add(new RadarTrackRecordService.HorizonStats(RadarTrackRecordService.HORIZONS[h], net.length,
                    net.length == 0 ? null : Arrays.stream(net).average().orElse(Double.NaN),
                    excess.length == 0 ? null : Arrays.stream(excess).average().orElse(Double.NaN),
                    net.length == 0 ? null : (double) Arrays.stream(net).filter(v -> v > 0).count() / net.length));
        }
        return new RadarTrackRecordService.Group(key, (int) picked.stream().map(Pick::date).distinct().count(), horizons);
    }
}
