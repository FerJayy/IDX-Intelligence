package com.idxintelligence.backend.signal;

import java.time.LocalDate;

/**
 * One day of prices for signal maths, adjusted for splits and dividends so that history is
 * comparable across corporate actions. Doubles are fine here: these feed statistics, not money.
 * open/high/low are NaN when missing; volume is 0 when missing.
 */
record Bar(LocalDate date, double open, double high, double low, double close, long volume) {

    boolean hasRange() {
        return !Double.isNaN(high) && !Double.isNaN(low) && high > low;
    }
}
