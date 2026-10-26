package com.idxintelligence.backend.signal;

import java.time.LocalDate;
import java.util.List;

/**
 * What is known about one stock at the close of one day, computed only from that day and
 * earlier. Nothing here may look at later bars; the backtest relies on that.
 *
 * @param closeLocation  where the close sits in the day's range, 0 = low, 1 = high; NaN if unknown
 * @param volumeRatio    today's volume / average of the previous 20 days; NaN if unknown
 * @param priorHigh20    highest high of the previous 20 days (today excluded)
 * @param avgValue20     average daily traded value (close x volume) of the last 20 days, IDR
 */
record Features(
        LocalDate date,
        double close,
        double high,
        double low,
        double return1,
        double return5,
        double volumeRatio,
        double closeLocation,
        double priorHigh20,
        double ma20,
        boolean ma20Rising,
        double avgValue20
) {

    static final int LOOKBACK = 20;

    boolean breakout() {
        return close > priorHigh20;
    }

    boolean aboveRisingMa20() {
        return close > ma20 && ma20Rising;
    }

    /** Features at index {@code i}, or null if there isn't enough history before it. */
    static Features at(List<Bar> bars, int i) {
        if (i < LOOKBACK + 5 || i >= bars.size()) return null;
        Bar today = bars.get(i);

        double priorHigh = Double.NEGATIVE_INFINITY;
        double volumeSum = 0;
        int volumeDays = 0;
        for (int k = i - LOOKBACK; k < i; k++) {
            Bar b = bars.get(k);
            priorHigh = Math.max(priorHigh, Double.isNaN(b.high()) ? b.close() : b.high());
            if (b.volume() > 0) {
                volumeSum += b.volume();
                volumeDays++;
            }
        }
        double avgVolume = volumeDays == 0 ? Double.NaN : volumeSum / volumeDays;

        double valueSum = 0;
        for (int k = i - LOOKBACK + 1; k <= i; k++) {
            valueSum += bars.get(k).close() * bars.get(k).volume();
        }

        double ma20 = movingAverage(bars, i);
        double ma20FiveDaysAgo = movingAverage(bars, i - 5);

        return new Features(
                today.date(),
                today.close(),
                today.high(),
                today.low(),
                today.close() / bars.get(i - 1).close() - 1,
                today.close() / bars.get(i - 5).close() - 1,
                today.volume() > 0 && avgVolume > 0 ? today.volume() / avgVolume : Double.NaN,
                today.hasRange() ? (today.close() - today.low()) / (today.high() - today.low()) : Double.NaN,
                priorHigh,
                ma20,
                ma20 > ma20FiveDaysAgo,
                valueSum / LOOKBACK);
    }

    private static double movingAverage(List<Bar> bars, int end) {
        double sum = 0;
        for (int k = end - LOOKBACK + 1; k <= end; k++) {
            sum += bars.get(k).close();
        }
        return sum / LOOKBACK;
    }
}
