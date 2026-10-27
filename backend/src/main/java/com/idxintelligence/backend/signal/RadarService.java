package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.Reason;
import com.idxintelligence.backend.broker.BrokerAnalysisService;
import com.idxintelligence.backend.broker.BrokerDailyActivity;
import com.idxintelligence.backend.broker.BrokerPosition;
import com.idxintelligence.backend.broker.DailyBrokerFlow;
import com.idxintelligence.backend.broker.FlowAssessment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broker Radar: ranks broker-tracked stocks by recent broker flow (persistence first), confirmed by
 * price, with the reasons behind each score. Also measures what IDX Edge's screener picks actually did.
 * The score is a ranking of what happened, not a forecast; its rules aren't backtested yet.
 */
@Service
public class RadarService {

    public static final List<Integer> WINDOWS = List.of(5, 10, 20);
    static final int[] OUTCOME_DAYS = {1, 3, 5};
    private static final int SCREENER_LOOKBACK_DAYS = 60;

    private final JdbcClient jdbc;
    private final SignalDataRepository prices;
    private final Map<String, Radar> cache = new ConcurrentHashMap<>();

    public RadarService(JdbcClient jdbc, SignalDataRepository prices) {
        this.jdbc = jdbc;
        this.prices = prices;
    }

    // ---------- API shapes ----------

    /** The broker with the longest run of consecutive net-buying days, ending on the latest day. */
    public record StreakBroker(String code, String name, int days, BigDecimal netValue, BigDecimal averageBuyPrice) {
    }

    /**
     * @param accumulationStreak consecutive latest days with top-5 net buying (positive) or selling (negative)
     * @param exitGap            last price / top buyers' average buy price - 1 (0.05 = 5% above their cost)
     * @param excludedBecause    filter that keeps this stock off the main ranking, or null
     */
    public record RadarRow(String ticker, String name, int score, FlowAssessment.Status status, int brokerDays,
                           int accumulationStreak, BigDecimal avgConcentration, BigDecimal foreignNetValue,
                           int foreignDays, StreakBroker streakBroker, BigDecimal buyersAveragePrice,
                           BigDecimal lastPrice, BigDecimal exitGap, Double return5d, boolean aboveRisingMa20,
                           List<Reason> reasons, Reason excludedBecause) {
    }

    /** One saved screener pick and what the price did afterwards (null until enough days have passed). */
    public record PickOutcome(LocalDate pickDate, String ticker, String bucket, BigDecimal claimedWinRate,
                              BigDecimal claimedPotential, BigDecimal entryClose, Double[] returns, Double[] excess) {
    }

    /** @param hitRate share of picks with a positive return; null when no pick is old enough yet */
    public record OutcomeSummary(int days, int picks, Double hitRate, Double averageReturn, Double averageExcess) {
    }

    public record Screener(List<PickOutcome> picks, List<OutcomeSummary> summary, BigDecimal averageClaimedWinRate) {
    }

    /**
     * One broker that net bought one stock several days in a row, ending on the stock's latest broker day.
     *
     * @param gap last price / the broker's average buy price over the run - 1
     */
    public record BrokerStockStreak(String ticker, String stockName, boolean hsc, String brokerCode, String brokerName,
                                    int days, long netVolume, BigDecimal netValue, BigDecimal averageBuyPrice,
                                    BigDecimal lastPrice, BigDecimal gap) {
    }

    public record Radar(OffsetDateTime generatedAt, LocalDate brokerDataDate, int windowDays, int trackedStocks,
                        List<RadarRow> rows, List<BrokerStockStreak> streaks, Screener screener) {
    }

    /** Streaks shorter than this aren't listed: two days in a row happens by chance all the time. */
    static final int MIN_STREAK_DAYS = 3;
    static final int MAX_STREAKS = 40;

    // ---------- computation ----------

    /** Cached until new broker or price data arrives. */
    public Radar radar(int windowDays) {
        int window = WINDOWS.contains(windowDays) ? windowDays : 10;
        String version = jdbc.sql("""
                SELECT coalesce((SELECT max(fetched_at)::text FROM broker_import_day), '') || '|' ||
                       coalesce((SELECT max(trade_date)::text FROM daily_price), '') || '|' ||
                       coalesce((SELECT max(fetched_at)::text FROM screener_pick), '') || '|' ||
                       (SELECT count(*) FROM stock WHERE broker_tracked AND active)
                """).query(String.class).single();
        String key = window + "|" + version;
        // Drop results computed from older data first (modifying the map inside computeIfAbsent isn't allowed).
        cache.keySet().removeIf(old -> old.startsWith(window + "|") && !old.equals(key));
        return cache.computeIfAbsent(key, k -> compute(window));
    }

    private Radar compute(int window) {
        LocalDate latestBrokerDay = jdbc.sql("SELECT max(trade_date) FROM broker_daily_activity")
                .query(LocalDate.class).optional().orElse(null);
        Map<String, String> names = new LinkedHashMap<>();
        java.util.Set<String> hsc = new java.util.HashSet<>();
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

        List<RadarRow> rows = new ArrayList<>();
        List<BrokerStockStreak> streaks = new ArrayList<>();
        if (latestBrokerDay != null && !names.isEmpty()) {
            // Enough calendar days to cover the window's trading days, plus holidays.
            Map<String, List<BrokerDailyActivity>> activity = activity(latestBrokerDay.minusDays(window * 2L + 14));
            Map<String, List<Bar>> bars = prices.adjustedBars(latestBrokerDay.minusDays(90), true);
            names.forEach((ticker, name) -> {
                RadarRow row = row(ticker, name, hsc.contains(ticker), window, activity.getOrDefault(ticker, List.of()),
                        bars.getOrDefault(ticker, List.of()), latestBrokerDay);
                if (row != null) rows.add(row);
                List<Bar> stockBars = bars.getOrDefault(ticker, List.of());
                BigDecimal last = stockBars.isEmpty() ? null
                        : BigDecimal.valueOf(stockBars.getLast().close()).setScale(0, RoundingMode.HALF_UP);
                streaks.addAll(streaks(ticker, name, hsc.contains(ticker), activity.getOrDefault(ticker, List.of()), last));
            });
        }
        streaks.sort(Comparator.comparingInt(BrokerStockStreak::days).reversed()
                .thenComparing(BrokerStockStreak::netValue, Comparator.reverseOrder()));
        rows.sort(Comparator.comparing((RadarRow r) -> r.excludedBecause() != null)
                .thenComparing(RadarRow::score, Comparator.reverseOrder())
                .thenComparing(RadarRow::ticker));
        return new Radar(OffsetDateTime.now(BacktestService.JAKARTA), latestBrokerDay, window, names.size(), rows,
                streaks.stream().limit(MAX_STREAKS).toList(), screener());
    }

    Map<String, List<BrokerDailyActivity>> activity(LocalDate from) {
        Map<String, List<BrokerDailyActivity>> byTicker = new HashMap<>();
        jdbc.sql("""
                SELECT a.ticker, a.trade_date, a.broker_code, b.name, a.buy_volume, a.buy_value, a.sell_volume, a.sell_value,
                       a.foreign_buy_value, a.foreign_sell_value
                FROM broker_daily_activity a
                JOIN broker b ON b.code = a.broker_code
                JOIN stock s ON s.ticker = a.ticker AND s.broker_tracked AND s.active
                WHERE a.trade_date >= ?
                """)
                .param(Date.valueOf(from))
                .query((rs, n) -> byTicker.computeIfAbsent(rs.getString("ticker"), t -> new ArrayList<>())
                        .add(new BrokerDailyActivity(rs.getDate("trade_date").toLocalDate(), rs.getString("broker_code"),
                                rs.getString("name"), rs.getLong("buy_volume"), rs.getBigDecimal("buy_value"),
                                rs.getLong("sell_volume"), rs.getBigDecimal("sell_value"),
                                rs.getBigDecimal("foreign_buy_value"), rs.getBigDecimal("foreign_sell_value"))))
                .list();
        return byTicker;
    }

    static RadarRow row(String ticker, String name, int window, List<BrokerDailyActivity> allRows, List<Bar> bars,
                        LocalDate latestBrokerDay) {
        return row(ticker, name, false, window, allRows, bars, latestBrokerDay);
    }

    /** @param hsc on IDX's High Shareholding Concentration list: listed separately, whatever its flow */
    static RadarRow row(String ticker, String name, boolean hsc, int window, List<BrokerDailyActivity> allRows,
                        List<Bar> bars, LocalDate latestBrokerDay) {
        // The window = this stock's last `window` broker days.
        List<LocalDate> days = allRows.stream().map(BrokerDailyActivity::date).distinct().sorted().toList();
        if (days.isEmpty()) return null;
        List<LocalDate> windowDays = days.subList(Math.max(0, days.size() - window), days.size());
        LocalDate since = windowDays.getFirst();
        List<BrokerDailyActivity> rows = allRows.stream().filter(r -> !r.date().isBefore(since)).toList();

        List<DailyBrokerFlow> flows = BrokerAnalysisService.dailyFlows(rows);
        List<BrokerPosition> positions = BrokerAnalysisService.positions(rows);
        List<BrokerPosition> topBuyers = positions.stream().filter(p -> p.netValue().signum() > 0)
                .sorted(Comparator.comparing(BrokerPosition::netValue).reversed()).limit(5).toList();

        Features features = bars.isEmpty() ? null : Features.at(bars, bars.size() - 1);
        BigDecimal lastPrice = bars.isEmpty() ? null : BigDecimal.valueOf(bars.getLast().close()).setScale(0, RoundingMode.HALF_UP);
        FlowAssessment assessment = BrokerAnalysisService.assess(flows, topBuyers, lastPrice);

        int streak = accumulationStreak(flows);
        BigDecimal avgConcentration = flows.stream().map(DailyBrokerFlow::concentration)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(flows.size()), 4, RoundingMode.HALF_UP);
        List<DailyBrokerFlow> foreignKnown = flows.stream().filter(f -> f.foreignNetValue() != null).toList();
        BigDecimal foreignNet = foreignKnown.isEmpty() ? null
                : foreignKnown.stream().map(DailyBrokerFlow::foreignNetValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        StreakBroker streakBroker = streakBroker(rows, windowDays);
        BigDecimal buyersAverage = averageBuyPrice(topBuyers);
        BigDecimal exitGap = buyersAverage == null || lastPrice == null ? null
                : lastPrice.divide(buyersAverage, 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);

        List<Reason> reasons = new ArrayList<>();
        int score = 0;
        if (!days.getLast().equals(latestBrokerDay)) {
            reasons.add(Reason.info("RADAR_BROKER_DATA_BEHIND", "latestDay", days.getLast()));
        }
        if (streak > 0) {
            score += add(reasons, Reason.of("RADAR_ACCUMULATION_STREAK", 8 * Math.min(streak, 5), "days", streak));
        } else if (streak < 0) {
            score += add(reasons, Reason.of("RADAR_DISTRIBUTION_STREAK", -8 * Math.min(-streak, 5), "days", -streak));
        }
        if (avgConcentration.compareTo(new BigDecimal("0.05")) > 0) {
            score += add(reasons, Reason.of("RADAR_CONCENTRATED_BUYING", 15, "avgConcentration", avgConcentration, "days", flows.size()));
        } else if (avgConcentration.compareTo(new BigDecimal("-0.05")) < 0) {
            score += add(reasons, Reason.of("RADAR_CONCENTRATED_SELLING", -15, "avgConcentration", avgConcentration, "days", flows.size()));
        }
        if (foreignNet != null && foreignNet.signum() != 0) {
            boolean bought = foreignNet.signum() > 0;
            score += add(reasons, Reason.of(bought ? "RADAR_FOREIGN_BUYING" : "RADAR_FOREIGN_SELLING", bought ? 10 : -10,
                    "value", foreignNet, "days", foreignKnown.size()));
        }
        if (streakBroker != null && streakBroker.days() >= 3) {
            score += add(reasons, Reason.of("RADAR_BROKER_STREAK", streakBroker.days() >= 5 ? 15 : 10,
                    "broker", streakBroker.code(), "name", streakBroker.name(), "days", streakBroker.days(),
                    "averagePrice", streakBroker.averageBuyPrice()));
        }
        if (features != null && features.aboveRisingMa20()) {
            score += add(reasons, Reason.of("RADAR_ABOVE_RISING_MA20", 10, "ma20", Math.round(features.ma20())));
        }
        if (exitGap != null) {
            double gap = exitGap.doubleValue();
            if (gap >= 0 && gap <= 0.10) {
                score += add(reasons, Reason.of("RADAR_NEAR_BUYERS_COST", 5, "gap", exitGap, "averagePrice", buyersAverage));
            } else if (gap > 0.20) {
                score += add(reasons, Reason.of("RADAR_FAR_ABOVE_BUYERS_COST", -10, "gap", exitGap, "averagePrice", buyersAverage));
            } else if (gap < -0.10) {
                reasons.add(Reason.info("RADAR_BELOW_BUYERS_COST", "gap", exitGap, "averagePrice", buyersAverage));
            }
        }

        Reason excluded = null;
        if (hsc) {
            // A few holders own almost all shares: broker flow and price can be steered, and IDX drops these from IHSG.
            excluded = Reason.info("HSC");
        } else if (features != null) {
            if (features.avgValue20() < MomentumScorer.MIN_AVG_VALUE) {
                excluded = Reason.info("LOW_LIQUIDITY", "avgValue", Math.round(features.avgValue20()),
                        "minValue", Math.round(MomentumScorer.MIN_AVG_VALUE));
            } else if (features.return1() >= MomentumScorer.NEAR_LIMIT_UP) {
                excluded = Reason.info("NEAR_LIMIT_UP", "change", features.return1());
            } else if (features.return5() > MomentumScorer.MAX_RETURN_5D) {
                excluded = Reason.info("CHASING", "change5d", features.return5(), "maxChange5d", MomentumScorer.MAX_RETURN_5D);
            }
        }
        return new RadarRow(ticker, name, score, assessment.status(), flows.size(), streak, avgConcentration,
                foreignNet, foreignKnown.size(), streakBroker, buyersAverage, lastPrice, exitGap,
                features == null ? null : features.return5(), features != null && features.aboveRisingMa20(),
                reasons, excluded);
    }

    /** Every broker with a net-buying run of at least {@link #MIN_STREAK_DAYS} ending on the stock's latest broker day. */
    static List<BrokerStockStreak> streaks(String ticker, String name, boolean hsc, List<BrokerDailyActivity> rows,
                                           BigDecimal lastPrice) {
        List<LocalDate> days = rows.stream().map(BrokerDailyActivity::date).distinct().sorted().toList();
        Map<String, Map<LocalDate, BrokerDailyActivity>> byBroker = new HashMap<>();
        for (BrokerDailyActivity r : rows) {
            byBroker.computeIfAbsent(r.brokerCode(), b -> new HashMap<>()).put(r.date(), r);
        }
        List<BrokerStockStreak> result = new ArrayList<>();
        byBroker.forEach((code, byDay) -> {
            int run = 0;
            long netVolume = 0;
            BigDecimal net = BigDecimal.ZERO;
            BigDecimal buyValue = BigDecimal.ZERO;
            long buyVolume = 0;
            String brokerName = null;
            for (int i = days.size() - 1; i >= 0; i--) {
                BrokerDailyActivity day = byDay.get(days.get(i));
                if (day == null || day.netVolume() <= 0) break;
                run++;
                netVolume += day.netVolume();
                net = net.add(day.netValue());
                buyValue = buyValue.add(day.buyValue());
                buyVolume += day.buyVolume();
                brokerName = Objects.requireNonNullElse(brokerName, day.brokerName());
            }
            if (run < MIN_STREAK_DAYS || buyVolume == 0) return;
            BigDecimal avg = buyValue.divide(BigDecimal.valueOf(buyVolume), 2, RoundingMode.HALF_UP);
            BigDecimal gap = lastPrice == null ? null : lastPrice.divide(avg, 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
            result.add(new BrokerStockStreak(ticker, name, hsc, code, brokerName, run, netVolume, net, avg, lastPrice, gap));
        });
        return result;
    }

    /** Consecutive latest days with top-5 net buying (+n) or net selling (-n); 0 if the last day is balanced. */
    static int accumulationStreak(List<DailyBrokerFlow> flows) {
        int streak = 0;
        for (int i = flows.size() - 1; i >= 0; i--) {
            int sign = flows.get(i).concentration().signum();
            if (sign == 0 || (streak != 0 && Integer.signum(streak) != sign)) break;
            streak += sign;
        }
        return streak;
    }

    /** For each broker, consecutive net-buying days ending on the last day; the longest run wins (then the biggest buyer). */
    static StreakBroker streakBroker(List<BrokerDailyActivity> rows, List<LocalDate> days) {
        Map<String, Map<LocalDate, BrokerDailyActivity>> byBroker = new HashMap<>();
        for (BrokerDailyActivity r : rows) {
            byBroker.computeIfAbsent(r.brokerCode(), b -> new TreeMap<>()).put(r.date(), r);
        }
        StreakBroker best = null;
        for (var entry : byBroker.entrySet()) {
            int run = 0;
            BigDecimal net = BigDecimal.ZERO;
            BigDecimal buyValue = BigDecimal.ZERO;
            long buyVolume = 0;
            String name = null;
            for (int i = days.size() - 1; i >= 0; i--) {
                BrokerDailyActivity day = entry.getValue().get(days.get(i));
                if (day == null || day.netVolume() <= 0) break;
                run++;
                net = net.add(day.netValue());
                buyValue = buyValue.add(day.buyValue());
                buyVolume += day.buyVolume();
                name = Objects.requireNonNullElse(name, day.brokerName());
            }
            if (run == 0) continue;
            BigDecimal avg = buyVolume == 0 ? null : buyValue.divide(BigDecimal.valueOf(buyVolume), 2, RoundingMode.HALF_UP);
            StreakBroker candidate = new StreakBroker(entry.getKey(), name, run, net, avg);
            if (best == null || candidate.days() > best.days()
                    || (candidate.days() == best.days() && candidate.netValue().compareTo(best.netValue()) > 0)) {
                best = candidate;
            }
        }
        return best;
    }

    private static BigDecimal averageBuyPrice(List<BrokerPosition> buyers) {
        long volume = buyers.stream().mapToLong(BrokerPosition::buyVolume).sum();
        if (volume == 0) return null;
        BigDecimal value = buyers.stream().map(BrokerPosition::buyValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return value.divide(BigDecimal.valueOf(volume), 2, RoundingMode.HALF_UP);
    }

    private static int add(List<Reason> reasons, Reason reason) {
        reasons.add(reason);
        return reason.points();
    }

    // ---------- screener track record ----------

    private Screener screener() {
        record Pick(LocalDate date, String ticker, String bucket, BigDecimal winRate, BigDecimal potential) {
        }
        List<Pick> picks = jdbc.sql("""
                SELECT pick_date, ticker, bucket, win_rate, potential FROM screener_pick
                WHERE pick_date >= current_date - ? ORDER BY pick_date DESC, ticker
                """)
                .param(SCREENER_LOOKBACK_DAYS)
                .query((rs, n) -> new Pick(rs.getDate("pick_date").toLocalDate(), rs.getString("ticker"),
                        rs.getString("bucket"), rs.getBigDecimal("win_rate"), rs.getBigDecimal("potential")))
                .list();
        if (picks.isEmpty()) {
            return new Screener(List.of(), summaries(List.of()), null);
        }
        LocalDate earliest = picks.stream().map(Pick::date).min(Comparator.naturalOrder()).orElseThrow();
        Map<String, TreeMap<LocalDate, BigDecimal>> closes = new HashMap<>();
        List<String> tickers = picks.stream().map(Pick::ticker).distinct().toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(tickers.size(), "?"));
        List<Object> params = new ArrayList<>(tickers);
        params.add(Date.valueOf(earliest));
        jdbc.sql("SELECT ticker, trade_date, close FROM daily_price WHERE ticker IN (" + placeholders + ") AND trade_date >= ?")
                .params(params)
                .query((rs, n) -> closes.computeIfAbsent(rs.getString("ticker"), t -> new TreeMap<>())
                        .put(rs.getDate("trade_date").toLocalDate(), rs.getBigDecimal("close")))
                .list();
        TreeMap<LocalDate, BigDecimal> ihsg = new TreeMap<>();
        jdbc.sql("SELECT trade_date, close FROM index_daily WHERE code = 'COMPOSITE' AND trade_date >= ?")
                .param(Date.valueOf(earliest))
                .query((rs, n) -> ihsg.put(rs.getDate("trade_date").toLocalDate(), rs.getBigDecimal("close")))
                .list();

        List<PickOutcome> outcomes = new ArrayList<>();
        for (Pick p : picks) {
            TreeMap<LocalDate, BigDecimal> series = closes.getOrDefault(p.ticker(), new TreeMap<>());
            BigDecimal entry = series.get(p.date());
            Double[] returns = new Double[OUTCOME_DAYS.length];
            Double[] excess = new Double[OUTCOME_DAYS.length];
            if (entry != null && entry.signum() > 0) {
                List<LocalDate> after = new ArrayList<>(series.tailMap(p.date(), false).keySet());
                for (int h = 0; h < OUTCOME_DAYS.length; h++) {
                    if (after.size() < OUTCOME_DAYS[h]) continue;
                    LocalDate exit = after.get(OUTCOME_DAYS[h] - 1);
                    double r = series.get(exit).doubleValue() / entry.doubleValue() - 1;
                    returns[h] = r;
                    BigDecimal in = ihsg.get(p.date());
                    BigDecimal out = ihsg.get(exit);
                    if (in != null && out != null) excess[h] = r - (out.doubleValue() / in.doubleValue() - 1);
                }
            }
            outcomes.add(new PickOutcome(p.date(), p.ticker(), p.bucket(), p.winRate(), p.potential(), entry, returns, excess));
        }
        List<BigDecimal> claimed = picks.stream().map(Pick::winRate).filter(Objects::nonNull).toList();
        BigDecimal avgClaimed = claimed.isEmpty() ? null
                : claimed.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(claimed.size()), 1, RoundingMode.HALF_UP);
        return new Screener(outcomes, summaries(outcomes), avgClaimed);
    }

    private static List<OutcomeSummary> summaries(List<PickOutcome> outcomes) {
        List<OutcomeSummary> result = new ArrayList<>();
        for (int h = 0; h < OUTCOME_DAYS.length; h++) {
            int index = h;
            List<Double> r = outcomes.stream().map(o -> o.returns()[index]).filter(Objects::nonNull).toList();
            List<Double> x = outcomes.stream().map(o -> o.excess()[index]).filter(Objects::nonNull).toList();
            result.add(new OutcomeSummary(OUTCOME_DAYS[h], r.size(),
                    r.isEmpty() ? null : r.stream().filter(v -> v > 0).count() / (double) r.size(),
                    r.isEmpty() ? null : r.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                    x.isEmpty() ? null : x.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        }
        return result;
    }
}
