package com.idxintelligence.backend.signal;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Replays the watchlist on history: every past day, score each stock using only data up to that
 * close (price features only; broker history is too short yet), take the top picks, then measure
 * what they did after buying at the next day's open. Returns are after trading costs and compared
 * with IHSG over the same days, and with buying every stock (the baseline).
 */
@Service
public class BacktestService {

    static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    /** Round trip: ~0,15% buy fee + ~0,25% sell fee incl. 0,1% sales tax. */
    static final double ROUND_TRIP_COST = 0.004;
    static final int PICKS_PER_DAY = 5;
    static final int[] HORIZONS = {1, 5};
    private static final int HISTORY_YEARS = 11;

    /** When the position is bought. */
    public enum Entry {
        /** Next day's opening price: you act the morning after the signal. */
        NEXT_OPEN,
        /** The signal day's close, e.g. in the pre-closing auction (15:50-16:00 WIB). Assumes the score
         *  computed shortly before the close is close to the final one. */
        SIGNAL_CLOSE
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BacktestService.class);
    private final SignalDataRepository data;
    private final Map<Entry, Result> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public BacktestService(SignalDataRepository data) {
        this.data = data;
    }

    /** Recomputed when new price data arrives; otherwise served from memory. */
    public Result result() {
        return result(Entry.NEXT_OPEN);
    }

    public synchronized Result result(Entry entry) {
        LocalDate latest = data.latestDate();
        Result cached = cache.get(entry);
        if (cached != null && Objects.equals(cached.to(), latest)) {
            return cached;
        }
        long started = System.nanoTime();
        LocalDate from = LocalDate.now(JAKARTA).minusYears(HISTORY_YEARS);
        Map<String, List<Bar>> bars = data.adjustedBars(from);
        Map<LocalDate, double[]> index = data.compositeIndex(from);
        long loaded = System.nanoTime();
        Result fresh = run(bars, index, latest, entry);
        log.info("Backtest {}: loaded data in {} ms, computed in {} ms", entry,
                (loaded - started) / 1_000_000, (System.nanoTime() - loaded) / 1_000_000);
        cache.put(entry, fresh);
        return fresh;
    }

    /** One scored stock-day with what happened next (NaN where unknown). */
    record Observation(LocalDate date, String ticker, int score, boolean listed, double[] excess, double[] net) {
    }

    static Result run(Map<String, List<Bar>> barsByTicker, Map<LocalDate, double[]> index, LocalDate latest) {
        return run(barsByTicker, index, latest, Entry.NEXT_OPEN);
    }

    static Result run(Map<String, List<Bar>> barsByTicker, Map<LocalDate, double[]> index, LocalDate latest, Entry entryMode) {
        Map<LocalDate, List<Observation>> byDate = new TreeMap<>();
        LocalDate first = null;
        for (var entry : barsByTicker.entrySet()) {
            List<Bar> bars = entry.getValue();
            for (int i = 0; i < bars.size() - 1; i++) {
                Features f = Features.at(bars, i);
                if (f == null) continue;
                MomentumScorer.Score score = MomentumScorer.score(f, null);

                double[] excess = new double[HORIZONS.length];
                double[] net = new double[HORIZONS.length];
                for (int h = 0; h < HORIZONS.length; h++) {
                    excess[h] = Double.NaN;
                    net[h] = Double.NaN;
                    int exit = i + HORIZONS[h];
                    if (exit >= bars.size()) continue;
                    Bar exitBar = bars.get(exit);
                    double[] ihsgOut = index.get(exitBar.date());
                    double stockIn;
                    double ihsgIn;
                    if (entryMode == Entry.NEXT_OPEN) {
                        Bar entryBar = bars.get(i + 1);
                        double[] ihsg = index.get(entryBar.date());
                        stockIn = entryBar.open();
                        ihsgIn = ihsg == null ? Double.NaN : ihsg[0];
                    } else {
                        double[] ihsg = index.get(bars.get(i).date());
                        stockIn = bars.get(i).close();
                        ihsgIn = ihsg == null ? Double.NaN : ihsg[1];
                    }
                    if (Double.isNaN(stockIn) || stockIn <= 0) continue;
                    net[h] = exitBar.close() / stockIn - 1 - ROUND_TRIP_COST;
                    if (ihsgOut != null && !Double.isNaN(ihsgIn) && ihsgIn > 0) {
                        excess[h] = net[h] - (ihsgOut[1] / ihsgIn - 1);
                    }
                }
                byDate.computeIfAbsent(f.date(), d -> new ArrayList<>())
                        .add(new Observation(f.date(), entry.getKey(), score.points(), score.listed(), excess, net));
                if (first == null || f.date().isBefore(first)) first = f.date();
            }
        }

        List<HorizonStats> horizons = new ArrayList<>();
        if (latest != null) {
            for (int h = 0; h < HORIZONS.length; h++) {
                for (Period period : Period.values()) {
                    horizons.add(stats(byDate, h, period, latest));
                }
            }
        }
        List<BucketStats> buckets = new ArrayList<>();
        int[][] bands = {{Integer.MIN_VALUE, 29}, {30, 49}, {50, 69}, {70, Integer.MAX_VALUE}};
        for (int[] band : bands) {
            buckets.add(bucket(byDate, band[0], band[1]));
        }

        List<String> notes = List.of(
                "Price signals only. Broker signals are part of the live score but not tested yet: broker history is too short.",
                (entryMode == Entry.NEXT_OPEN ? "Buy at the next day's open" : "Buy at the signal day's close (pre-closing auction)")
                        + "; sell at the close 1 or 5 trading days later. " + pct(ROUND_TRIP_COST) + " trading costs per round trip included.",
                "'Excess' = return after costs minus IHSG over the same days. Baseline = buying every stock in the list every day.",
                "Universe is today's " + barsByTicker.size() + " large, liquid stocks; stocks that were delisted or shrank aren't included, which flatters results somewhat (survivorship bias).",
                "Past results don't guarantee future results.");
        return new Result(OffsetDateTime.now(JAKARTA), entryMode, first, latest, barsByTicker.size(), ROUND_TRIP_COST,
                PICKS_PER_DAY, MomentumScorer.MIN_SCORE, horizons, buckets, notes);
    }

    enum Period {
        LAST_YEAR("Last 12 months", 1), LAST_3_YEARS("Last 3 years", 3), ALL("All history", 100);

        final String label;
        final int years;

        Period(String label, int years) {
            this.label = label;
            this.years = years;
        }
    }

    private static HorizonStats stats(Map<LocalDate, List<Observation>> byDate, int h, Period period, LocalDate latest) {
        LocalDate since = latest.minusYears(period.years);
        List<Double> picks = new ArrayList<>();
        List<Double> pickNet = new ArrayList<>();
        List<Double> baseline = new ArrayList<>();
        int daysWithPicks = 0;
        for (var day : byDate.entrySet()) {
            if (day.getKey().isBefore(since)) continue;
            List<Observation> valid = day.getValue().stream().filter(o -> !Double.isNaN(o.excess()[h])).toList();
            if (valid.isEmpty()) continue;
            // Baseline: the average of all stocks that day counts as one equal-weight "buy everything" trade.
            baseline.add(valid.stream().mapToDouble(o -> o.excess()[h]).average().orElse(Double.NaN));
            List<Observation> top = valid.stream()
                    .filter(Observation::listed)
                    .sorted(Comparator.comparingInt(Observation::score).reversed())
                    .limit(PICKS_PER_DAY)
                    .toList();
            if (!top.isEmpty()) daysWithPicks++;
            top.forEach(o -> {
                picks.add(o.excess()[h]);
                pickNet.add(o.net()[h]);
            });
        }
        return new HorizonStats(HORIZONS[h], period.label, picks.size(), daysWithPicks,
                known(hitRate(picks)), known(mean(picks)), known(median(picks)), known(mean(pickNet)),
                known(mean(baseline)), known(hitRate(baseline)));
    }

    private static BucketStats bucket(Map<LocalDate, List<Observation>> byDate, int min, int max) {
        List<Double> one = new ArrayList<>();
        List<Double> five = new ArrayList<>();
        for (List<Observation> day : byDate.values()) {
            for (Observation o : day) {
                if (o.score() < min || o.score() > max) continue;
                if (!Double.isNaN(o.excess()[0])) one.add(o.excess()[0]);
                if (!Double.isNaN(o.excess()[1])) five.add(o.excess()[1]);
            }
        }
        String label = min == Integer.MIN_VALUE ? "below 30" : max == Integer.MAX_VALUE ? min + " and up" : min + "–" + max;
        return new BucketStats(label, one.size(), known(mean(one)), known(hitRate(one)),
                known(mean(five)), known(hitRate(five)));
    }

    /** NaN (no data) becomes null, so JSON shows "missing" rather than a number. */
    private static Double known(double value) {
        return Double.isNaN(value) ? null : value;
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }

    private static double hitRate(List<Double> values) {
        return values.isEmpty() ? Double.NaN : values.stream().filter(v -> v > 0).count() / (double) values.size();
    }

    private static String pct(double fraction) {
        return String.format(java.util.Locale.forLanguageTag("id-ID"), "%.1f%%", fraction * 100);
    }

    /**
     * Numbers are fractions (0.012 = 1,2%); null when there were no observations.
     *
     * @param hitRate            share of picks that beat IHSG after costs
     * @param avgExcess          average return after costs minus IHSG
     * @param avgNetReturn       average return after costs (not compared with IHSG)
     * @param baselineAvgExcess  same, for buying every stock each day
     */
    public record HorizonStats(int horizonDays, String period, int picks, int daysWithPicks, Double hitRate,
                               Double avgExcess, Double medianExcess, Double avgNetReturn,
                               Double baselineAvgExcess, Double baselineHitRate) {
    }

    /** All scored stock-days in a score band (not only the top picks): does a higher score do better? */
    public record BucketStats(String scoreBand, int observations, Double avgExcess1d, Double hitRate1d,
                              Double avgExcess5d, Double hitRate5d) {
    }

    public record Result(OffsetDateTime generatedAt, Entry entry, LocalDate from, LocalDate to, int stocks, double roundTripCost,
                         int picksPerDay, int minScore, List<HorizonStats> horizons, List<BucketStats> scoreBands,
                         List<String> notes) {
    }
}
