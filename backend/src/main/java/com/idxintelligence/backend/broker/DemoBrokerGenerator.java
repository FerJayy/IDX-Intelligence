package com.idxintelligence.backend.broker;

import com.idxintelligence.backend.market.PriceBar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Invents broker summary rows that are internally consistent with a demo price
 * history: every day, total lots bought equals total lots sold equals the bar's
 * volume, and average prices fall inside the bar's high-low range.
 *
 * <p>Flows are deliberately NOT predictive of future prices. They only react to
 * the same day's price move (foreign brokers lean to the buy side on up days) and
 * include one "persistent" broker per stock that buys or sells in random multi-week
 * stretches. Anything predictive here would make later backtests on demo data
 * look falsely good.
 */
final class DemoBrokerGenerator {

    private static final long LOT = 100;

    // Codes only: activity is invented, so it is not attributed to named firms.
    static final List<Broker> BROKERS = List.of(
            new Broker("AK", true), new Broker("BK", true),
            new Broker("KZ", true), new Broker("RX", true),
            new Broker("ZP", true), new Broker("YU", true),
            new Broker("CG", true),
            new Broker("YP", false), new Broker("PD", false),
            new Broker("CC", false), new Broker("XL", false),
            new Broker("NI", false), new Broker("OD", false),
            new Broker("LG", false), new Broker("DX", false),
            new Broker("SQ", false), new Broker("EP", false),
            new Broker("KK", false), new Broker("CP", false),
            new Broker("MG", false)
    );

    record Broker(String code, boolean foreign) {
    }

    private DemoBrokerGenerator() {
    }

    static List<BrokerDailyActivity> generate(String ticker, List<PriceBar> bars) {
        if (bars.isEmpty()) return List.of();
        Random random = new Random(ticker.hashCode() * 17L + bars.getLast().date().toEpochDay());
        int n = BROKERS.size();

        // Each stock has its own broker popularity ranking (heavier weight = more market share).
        double[] popularity = new double[n];
        for (int i = 0; i < n; i++) {
            popularity[i] = 1 / Math.pow(1 + random.nextInt(n), 0.9);
        }

        int persistent = random.nextInt(n);
        double persistentBias = 1;
        int regimeDaysLeft = 0;

        List<BrokerDailyActivity> rows = new ArrayList<>();
        double previousClose = bars.getFirst().open().doubleValue();
        for (PriceBar bar : bars) {
            if (regimeDaysLeft-- <= 0) {
                regimeDaysLeft = 15 + random.nextInt(40);
                // Buy-leaning, sell-leaning or quiet stretch, chosen at random.
                persistentBias = switch (random.nextInt(3)) {
                    case 0 -> 2.5 + random.nextDouble() * 2;
                    case 1 -> 1 / (2.5 + random.nextDouble() * 2);
                    default -> 1;
                };
            }

            double close = bar.close().doubleValue();
            boolean upDay = close >= previousClose;
            previousClose = close;

            double[] buyWeights = new double[n];
            double[] sellWeights = new double[n];
            for (int i = 0; i < n; i++) {
                double foreignTilt = BROKERS.get(i).foreign() ? (upDay ? 1.3 : 0.77) : 1;
                buyWeights[i] = popularity[i] * noise(random) * foreignTilt;
                sellWeights[i] = popularity[i] * noise(random) / foreignTilt;
            }
            buyWeights[persistent] *= persistentBias;
            sellWeights[persistent] /= persistentBias;

            long totalLots = bar.volume() == null ? 0 : bar.volume() / LOT;
            long[] buyLots = allocate(totalLots, buyWeights);
            long[] sellLots = allocate(totalLots, sellWeights);

            for (int i = 0; i < n; i++) {
                if (buyLots[i] == 0 && sellLots[i] == 0) continue;
                BigDecimal buyValue = value(buyLots[i], averagePrice(bar, random));
                BigDecimal sellValue = value(sellLots[i], averagePrice(bar, random));
                // Demo simplification: all of a foreign broker's flow counts as foreign investors'.
                boolean foreign = BROKERS.get(i).foreign();
                rows.add(new BrokerDailyActivity(
                        bar.date(),
                        BROKERS.get(i).code(),
                        null,
                        buyLots[i] * LOT,
                        buyValue,
                        sellLots[i] * LOT,
                        sellValue,
                        foreign ? buyValue : BigDecimal.ZERO,
                        foreign ? sellValue : BigDecimal.ZERO));
            }
        }
        return rows;
    }

    private static double noise(Random random) {
        return Math.exp(random.nextGaussian() * 0.6);
    }

    /** Splits {@code total} lots by weight so the parts add up exactly. */
    private static long[] allocate(long total, double[] weights) {
        double sum = 0;
        for (double w : weights) sum += w;
        long[] lots = new long[weights.length];
        long assigned = 0;
        int largest = 0;
        for (int i = 0; i < weights.length; i++) {
            lots[i] = (long) Math.floor(total * weights[i] / sum);
            assigned += lots[i];
            if (weights[i] > weights[largest]) largest = i;
        }
        lots[largest] += total - assigned;
        return lots;
    }

    private static double averagePrice(PriceBar bar, Random random) {
        double low = bar.low().doubleValue();
        double high = bar.high().doubleValue();
        return low + random.nextDouble() * (high - low);
    }

    private static BigDecimal value(long lots, double averagePrice) {
        return BigDecimal.valueOf(averagePrice)
                .setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(lots * LOT))
                .setScale(0, RoundingMode.HALF_UP);
    }
}
