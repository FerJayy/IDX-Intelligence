package com.idxintelligence.backend.market;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Builds a plausible but invented daily price history that ends exactly at a
 * quote's previous close and last price, so the chart agrees with the dashboard.
 * Deterministic per ticker and end date. Skips weekends but not IDX holidays.
 */
final class DemoPriceGenerator {

    private static final double DAILY_VOLATILITY = 0.02;

    /** IDX regular-market lot: 100 shares. */
    static final long LOT_SIZE = 100;

    private DemoPriceGenerator() {
    }

    static List<PriceBar> generate(StockQuote quote, LocalDate end, int tradingDays) {
        Random random = new Random(quote.ticker().hashCode() * 31L + end.toEpochDay());
        List<LocalDate> dates = tradingDaysEndingOn(end, tradingDays);

        // Walk closes backwards from the known last two closes.
        double[] closes = new double[tradingDays];
        closes[tradingDays - 1] = quote.lastPrice().doubleValue();
        closes[tradingDays - 2] = quote.previousClose().doubleValue();
        for (int i = tradingDays - 3; i >= 0; i--) {
            double dailyReturn = random.nextGaussian() * DAILY_VOLATILITY;
            closes[i] = roundToTick(closes[i + 1] / (1 + dailyReturn));
        }

        double baseVolume = quote.volume() == null ? 10_000_000 : quote.volume();
        List<PriceBar> bars = new ArrayList<>(tradingDays);
        for (int i = 0; i < tradingDays; i++) {
            double close = closes[i];
            double reference = i == 0 ? close : closes[i - 1];
            double open = roundToTick(reference * (1 + random.nextGaussian() * 0.005));
            double high = roundToTick(Math.max(open, close) * (1 + Math.abs(random.nextGaussian()) * 0.01));
            double low = roundToTick(Math.min(open, close) * (1 - Math.abs(random.nextGaussian()) * 0.01));
            long volume = i == tradingDays - 1 && quote.volume() != null
                    ? quote.volume()
                    : Math.round(baseVolume * Math.exp(random.nextGaussian() * 0.4) / LOT_SIZE) * LOT_SIZE;

            bars.add(new PriceBar(dates.get(i),
                    price(open),
                    price(Math.max(high, Math.max(open, close))),
                    price(Math.min(low, Math.min(open, close))),
                    price(close),
                    volume));
        }
        return bars;
    }

    /** IDX price fractions (tick sizes) for regular-market stocks. */
    static double tickSize(double price) {
        if (price < 200) return 1;
        if (price < 500) return 2;
        if (price < 2_000) return 5;
        if (price < 5_000) return 10;
        return 25;
    }

    private static double roundToTick(double price) {
        double tick = tickSize(price);
        return Math.max(1, Math.round(price / tick) * tick);
    }

    private static BigDecimal price(double value) {
        return BigDecimal.valueOf(Math.round(value));
    }

    private static List<LocalDate> tradingDaysEndingOn(LocalDate end, int count) {
        List<LocalDate> dates = new ArrayList<>(count);
        LocalDate date = end;
        while (dates.size() < count) {
            if (isWeekday(date)) {
                dates.add(date);
            }
            date = date.minusDays(1);
        }
        Collections.reverse(dates);
        return dates;
    }

    static boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
