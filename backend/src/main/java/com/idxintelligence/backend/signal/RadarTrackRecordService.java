package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.broker.BrokerDailyActivity;
import com.idxintelligence.backend.broker.FlowAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * How the Broker Radar would have done: replays it on every past broker day using only data up to
 * that day, then measures what each stock did next. Broker data appears in the evening, so the
 * simulated entry is the next day's open; exits are closes 1, 3 and 5 trading days later, after the
 * same 0,4% round-trip cost as the momentum backtest, and compared with IHSG over the same period.
 */
@Service
public class RadarTrackRecordService {

    private static final Logger log = LoggerFactory.getLogger(RadarTrackRecordService.class);
    static final int[] HORIZONS = {1, 3, 5};
    static final int TOP_PICKS = 3;
    static final int HIGH_SCORE = 50;
    /** Days before the first broker day that Features needs (MA20 five days ago, 20-day averages). */
    private static final int PRICE_LEAD_DAYS = 90;

    private final JdbcClient jdbc;
    private final RadarService radar;
    private final SignalDataRepository prices;
    private final Map<String, TrackRecord> cache = new ConcurrentHashMap<>();

    public RadarTrackRecordService(JdbcClient jdbc, RadarService radar, SignalDataRepository prices) {
        this.jdbc = jdbc;
        this.radar = radar;
        this.prices = prices;
    }

    /** @param net after costs; NaN when the horizon hasn't happened yet */
    record Observation(LocalDate date, String ticker, int score, FlowAssessment.Status status, boolean listed,
                       double[] net, double[] excess) {
    }

    /** @param hitRate share of trades with a positive return after costs */
    public record HorizonStats(int days, int trades, Double averageReturn, Double averageExcess, Double hitRate) {
    }

    /** @param group TOP_PICKS, HIGH_SCORE, ACCUMULATION, NEUTRAL, DISTRIBUTION or ALL */
    public record Group(String group, int signalDays, List<HorizonStats> horizons) {
    }

    /**
     * @param signalDays distinct broker days replayed (the honest sample size: trades on the same day move together)
     */
    public record TrackRecord(OffsetDateTime generatedAt, int windowDays, LocalDate from, LocalDate to, int signalDays,
                              int stocks, double roundTripCost, int topPicks, int highScore, List<Group> groups) {
    }

    /** Cached until new broker or price data arrives. */
    public TrackRecord trackRecord(int windowDays) {
        int window = RadarService.WINDOWS.contains(windowDays) ? windowDays : 10;
        String version = jdbc.sql("""
                SELECT coalesce((SELECT max(fetched_at)::text FROM broker_import_day), '') || '|' ||
                       coalesce((SELECT max(trade_date)::text FROM daily_price), '') || '|' ||
                       (SELECT count(*) FROM stock WHERE broker_tracked AND active)
                """).query(String.class).single();
        String key = window + "|" + version;
        cache.keySet().removeIf(old -> old.startsWith(window + "|") && !old.equals(key));
        return cache.computeIfAbsent(key, k -> load(window));
    }

    private TrackRecord load(int window) {
        long started = System.nanoTime();
        LocalDate firstBrokerDay = jdbc.sql("SELECT min(trade_date) FROM broker_daily_activity")
                .query(LocalDate.class).optional().orElse(null);
        if (firstBrokerDay == null) {
            return compute(window, Map.of(), Map.of(), Set.of(), Map.of(), Map.of());
        }
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
        Map<String, List<BrokerDailyActivity>> activity = radar.activity(firstBrokerDay);
        Map<String, List<Bar>> bars = prices.adjustedBars(firstBrokerDay.minusDays(PRICE_LEAD_DAYS), true);
        Map<LocalDate, double[]> index = prices.compositeIndex(firstBrokerDay.minusDays(PRICE_LEAD_DAYS));
        TrackRecord result = compute(window, names, activity, hsc, bars, index);
        log.info("Radar track record ({} days): {} signal days in {} ms", window, result.signalDays(),
                (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    static TrackRecord compute(int window, Map<String, String> names, Map<String, List<BrokerDailyActivity>> activity,
                               Set<String> hsc, Map<String, List<Bar>> barsByTicker, Map<LocalDate, double[]> index) {
        List<Observation> observations = observations(window, names, activity, hsc, barsByTicker, index);
        TreeMap<LocalDate, List<Observation>> byDate = new TreeMap<>();
        observations.forEach(o -> byDate.computeIfAbsent(o.date(), d -> new ArrayList<>()).add(o));

        // The daily top picks: listed rows only (same filters as the live ranking), highest score first.
        Set<Observation> top = new HashSet<>();
        byDate.values().forEach(day -> day.stream().filter(Observation::listed)
                .sorted(Comparator.comparingInt(Observation::score).reversed().thenComparing(Observation::ticker))
                .limit(TOP_PICKS).forEach(top::add));

        List<Group> groups = List.of(
                group("TOP_PICKS", observations, top::contains),
                group("HIGH_SCORE", observations, o -> o.listed() && o.score() >= HIGH_SCORE),
                group("ACCUMULATION", observations, o -> o.listed() && o.status() == FlowAssessment.Status.ACCUMULATION),
                group("NEUTRAL", observations, o -> o.listed() && o.status() == FlowAssessment.Status.NEUTRAL),
                group("DISTRIBUTION", observations, o -> o.listed() && o.status() == FlowAssessment.Status.DISTRIBUTION),
                group("ALL", observations, o -> true));
        return new TrackRecord(OffsetDateTime.now(BacktestService.JAKARTA), window,
                byDate.isEmpty() ? null : byDate.firstKey(), byDate.isEmpty() ? null : byDate.lastKey(),
                byDate.size(), (int) observations.stream().map(Observation::ticker).distinct().count(),
                BacktestService.ROUND_TRIP_COST, TOP_PICKS, HIGH_SCORE, groups);
    }

    /** One Radar row per stock per broker day, scored only from data up to that day. */
    static List<Observation> observations(int window, Map<String, String> names,
                                          Map<String, List<BrokerDailyActivity>> activity, Set<String> hsc,
                                          Map<String, List<Bar>> barsByTicker, Map<LocalDate, double[]> index) {
        List<Observation> result = new ArrayList<>();
        names.forEach((ticker, name) -> {
            List<BrokerDailyActivity> rows = activity.getOrDefault(ticker, List.of()).stream()
                    .sorted(Comparator.comparing(BrokerDailyActivity::date)).toList();
            List<Bar> bars = barsByTicker.getOrDefault(ticker, List.of());
            if (rows.isEmpty() || bars.isEmpty()) return;
            Map<LocalDate, Integer> barIndex = new TreeMap<>();
            for (int i = 0; i < bars.size(); i++) barIndex.put(bars.get(i).date(), i);

            List<LocalDate> days = rows.stream().map(BrokerDailyActivity::date).distinct().toList();
            int rowEnd = 0;
            for (int d = 0; d < days.size(); d++) {
                LocalDate day = days.get(d);
                while (rowEnd < rows.size() && !rows.get(rowEnd).date().isAfter(day)) rowEnd++;
                // A full window of broker days is needed, as on the live page.
                if (d + 1 < window) continue;
                Integer i = barIndex.get(day);
                if (i == null || i + 1 >= bars.size()) continue;   // no next day to enter on yet
                RadarService.RadarRow row = RadarService.row(ticker, name, hsc.contains(ticker), window,
                        rows.subList(0, rowEnd), bars.subList(0, i + 1), day);
                if (row == null) continue;
                result.add(new Observation(day, ticker, row.score(), row.status(), row.excludedBecause() == null,
                        outcome(bars, i, index, false), outcome(bars, i, index, true)));
            }
        });
        return result;
    }

    /** Buy at the next open, sell at the close h days after the signal; NaN where unknown. */
    private static double[] outcome(List<Bar> bars, int signal, Map<LocalDate, double[]> index, boolean excess) {
        double[] out = new double[HORIZONS.length];
        Bar entry = bars.get(signal + 1);
        for (int h = 0; h < HORIZONS.length; h++) {
            out[h] = Double.NaN;
            int exit = signal + HORIZONS[h];
            if (exit >= bars.size() || entry.open() <= 0) continue;
            double net = bars.get(exit).close() / entry.open() - 1 - BacktestService.ROUND_TRIP_COST;
            if (!excess) {
                out[h] = net;
                continue;
            }
            double[] ihsgIn = index.get(entry.date());
            double[] ihsgOut = index.get(bars.get(exit).date());
            if (ihsgIn == null || ihsgOut == null || Double.isNaN(ihsgIn[0]) || ihsgIn[0] <= 0) continue;
            out[h] = net - (ihsgOut[1] / ihsgIn[0] - 1);
        }
        return out;
    }

    private static Group group(String key, List<Observation> all, Predicate<Observation> member) {
        List<Observation> picked = all.stream().filter(member).toList();
        List<HorizonStats> horizons = new ArrayList<>();
        for (int h = 0; h < HORIZONS.length; h++) {
            int k = h;
            double[] net = picked.stream().mapToDouble(o -> o.net()[k]).filter(v -> !Double.isNaN(v)).toArray();
            double[] excess = picked.stream().mapToDouble(o -> o.excess()[k]).filter(v -> !Double.isNaN(v)).toArray();
            horizons.add(new HorizonStats(HORIZONS[h], net.length, average(net), average(excess),
                    net.length == 0 ? null : (double) java.util.Arrays.stream(net).filter(v -> v > 0).count() / net.length));
        }
        return new Group(key, (int) picked.stream().map(Observation::date).distinct().count(), horizons);
    }

    private static Double average(double[] values) {
        return values.length == 0 ? null : java.util.Arrays.stream(values).average().orElse(Double.NaN);
    }
}
