package com.idxintelligence.backend.market;

import java.math.BigDecimal;

/**
 * One chart candle.
 *
 * @param time   start of the candle as epoch seconds (UTC). Daily and longer candles start at
 *               00:00 UTC of their first trading date, so the date never shifts across time zones.
 * @param open   null when missing (never 0); likewise high/low/volume
 */
public record Candle(long time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, Long volume) {
}
