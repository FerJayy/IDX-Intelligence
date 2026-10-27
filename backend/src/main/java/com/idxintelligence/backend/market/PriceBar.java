package com.idxintelligence.backend.market;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trading day of OHLCV data. Prices in IDR, volume in shares.
 *
 * @param adjustedClose close adjusted for splits and dividends; null if unknown (e.g. demo data)
 */
public record PriceBar(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume,
        BigDecimal adjustedClose
) {
    public PriceBar(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, Long volume) {
        this(date, open, high, low, close, volume, null);
    }
}
