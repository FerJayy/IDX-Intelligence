package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.Reason;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores a stock for the "strength / breakout" watchlist from end-of-day features. Each rule adds
 * or subtracts points and records a reason code with its numbers. Thresholds are starting points;
 * the backtest shows whether they help. The score is a ranking, not a probability.
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

    /** @param excludedBecause non-null when the stock is filtered out; points and reasons still filled for display */
    record Score(int points, List<Reason> reasons, Reason excludedBecause) {
        boolean listed() {
            return excludedBecause == null && points >= MIN_SCORE;
        }
    }

    /** @param broker recent broker flow, or null when not available (e.g. in the price-only backtest) */
    static Score score(Features f, BrokerSignal broker) {
        List<Reason> reasons = new ArrayList<>();

        if (f.breakout()) {
            reasons.add(Reason.of("BREAKOUT_20D", 25, "level", Math.round(f.priorHigh20())));
        }
        if (f.volumeRatio() >= 2.0) {
            reasons.add(Reason.of("VOLUME_SPIKE", 25, "ratio", f.volumeRatio()));
        } else if (f.volumeRatio() >= 1.5) {
            reasons.add(Reason.of("VOLUME_SPIKE", 15, "ratio", f.volumeRatio()));
        }
        if (f.closeLocation() >= 0.8) {
            reasons.add(Reason.of("CLOSE_NEAR_HIGH", 15, "location", f.closeLocation()));
        }
        if (f.return1() > 0) {
            reasons.add(Reason.of("UP_TODAY", 10, "change", f.return1()));
        }
        if (f.aboveRisingMa20()) {
            reasons.add(Reason.of("ABOVE_RISING_MA20", 15, "ma20", Math.round(f.ma20())));
        }

        if (broker != null) {
            if (broker.avgConcentration() > 0.05) {
                reasons.add(Reason.of("BROKERS_NET_BUYING", 10, "avgConcentration", broker.avgConcentration(), "days", broker.days()));
            } else if (broker.avgConcentration() < -0.05) {
                reasons.add(Reason.of("BROKERS_NET_SELLING", -10, "avgConcentration", broker.avgConcentration(), "days", broker.days()));
            }
            if (broker.foreignNetValue() != null && broker.foreignNetValue().signum() != 0) {
                boolean bought = broker.foreignNetValue().signum() > 0;
                reasons.add(Reason.of(bought ? "FOREIGN_NET_BUY_RECENT" : "FOREIGN_NET_SELL_RECENT", bought ? 10 : -10,
                        "value", broker.foreignNetValue(), "days", broker.days()));
            }
        }
        int points = reasons.stream().mapToInt(Reason::points).sum();

        Reason excluded = null;
        if (f.avgValue20() < MIN_AVG_VALUE) {
            excluded = Reason.info("LOW_LIQUIDITY", "avgValue", Math.round(f.avgValue20()), "minValue", Math.round(MIN_AVG_VALUE));
        } else if (f.return1() >= NEAR_LIMIT_UP) {
            excluded = Reason.info("NEAR_LIMIT_UP", "change", f.return1());
        } else if (f.return5() > MAX_RETURN_5D) {
            excluded = Reason.info("CHASING", "change5d", f.return5(), "maxChange5d", MAX_RETURN_5D);
        }
        return new Score(points, reasons, excluded);
    }
}
