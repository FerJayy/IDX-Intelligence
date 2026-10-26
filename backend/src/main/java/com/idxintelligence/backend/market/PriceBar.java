package com.idxintelligence.backend.market;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One trading day of OHLCV data. Prices in IDR, volume in shares. */
public record PriceBar(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {
}
