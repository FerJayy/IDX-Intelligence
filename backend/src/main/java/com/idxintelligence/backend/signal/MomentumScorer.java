package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.IndonesianFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores a stock for the "strength / breakout" watchlist from end-of-day features. Each rule adds
 * or subtracts points and records a human-readable reason. Thresholds are starting points; the
 * backtest shows whether they help. The score is a ranking, not a probability.
 */
final class MomentumScorer {

    /** Minimum score to appear on the watchlist. */
    static final int MIN_SCORE = 50;

    // Filters: stocks we don't list at all, however high they score.
    static final double MIN_AVG_VALUE = 5_000_000_000d;   // Rp5 M per day: easy to get in and out
    static final double MAX_RETURN_5D = 0.15;             // already up >15% in 5 days: chasing
    static final double NEAR_LIMIT_UP = 0.18;             // up >=18% today: likely at the daily limit, hard to buy

    private MomentumScorer() {
    }

    record Reason(String text, int points) {
    }

    /** @param excludedBecause non-null when the stock is filtered out; score and reasons still filled for display */
    record Score(int points, List<Reason> reasons, String excludedBecause) {
        boolean listed() {
            return excludedBecause == null && points >= MIN_SCORE;
        }
    }

    /** @param broker recent broker flow, or null when not available (e.g. in the price-only backtest) */
    static Score score(Features f, BrokerSignal broker) {
        List<Reason> reasons = new ArrayList<>();
        int points = 0;

        if (f.breakout()) {
            points += add(reasons, 25, "Closed above its 20-day high (" + IndonesianFormat.integer(Math.round(f.priorHigh20())) + ")");
        }
        if (f.volumeRatio() >= 2.0) {
            points += add(reasons, 25, "Volume " + IndonesianFormat.decimal(f.volumeRatio()) + "× its 20-day average");
        } else if (f.volumeRatio() >= 1.5) {
            points += add(reasons, 15, "Volume " + IndonesianFormat.decimal(f.volumeRatio()) + "× its 20-day average");
        }
        if (f.closeLocation() >= 0.8) {
            points += add(reasons, 15, "Closed near the day's high (in the top fifth of its range)");
        }
        if (f.return1() > 0) {
            points += add(reasons, 10, "Up " + IndonesianFormat.signedPercent(f.return1()) + " today");
        }
        if (f.aboveRisingMa20()) {
            points += add(reasons, 15, "Above a rising 20-day average");
        }

        if (broker != null) {
            if (broker.avgConcentration() > 0.05) {
                points += add(reasons, 10, "Top-5 brokers net buying: avg " + IndonesianFormat.signedPercent(broker.avgConcentration())
                        + " of volume over the last " + broker.days() + " days");
            } else if (broker.avgConcentration() < -0.05) {
                points += add(reasons, -10, "Top-5 brokers net selling: avg " + IndonesianFormat.signedPercent(broker.avgConcentration())
                        + " of volume over the last " + broker.days() + " days");
            }
            if (broker.foreignNetValue() != null && broker.foreignNetValue().signum() != 0) {
                boolean bought = broker.foreignNetValue().signum() > 0;
                points += add(reasons, bought ? 10 : -10, "Foreign investors net " + (bought ? "bought" : "sold") + " Rp"
                        + IndonesianFormat.compact(broker.foreignNetValue().abs()) + " over the last " + broker.days() + " days");
            }
        }

        String excluded = null;
        if (f.avgValue20() < MIN_AVG_VALUE) {
            excluded = "Low liquidity: averages Rp" + IndonesianFormat.compact(java.math.BigDecimal.valueOf(f.avgValue20())) + " traded per day";
        } else if (f.return1() >= NEAR_LIMIT_UP) {
            excluded = "Up " + IndonesianFormat.signedPercent(f.return1()) + " today, likely at the daily limit: hard to buy at the open";
        } else if (f.return5() > MAX_RETURN_5D) {
            excluded = "Already up " + IndonesianFormat.signedPercent(f.return5()) + " in 5 days: high risk of chasing";
        }
        return new Score(points, reasons, excluded);
    }

    private static int add(List<Reason> reasons, int points, String text) {
        reasons.add(new Reason(text, points));
        return points;
    }
}
