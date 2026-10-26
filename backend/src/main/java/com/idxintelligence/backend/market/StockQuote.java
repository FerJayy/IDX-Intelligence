package com.idxintelligence.backend.market;

import java.math.BigDecimal;

/**
 * End-of-day quote for one IDX stock. Prices are in IDR.
 * Nullable fields mean "not available", which is different from zero.
 */
public record StockQuote(
        String ticker,
        String name,
        String sector,
        BigDecimal lastPrice,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePercent,
        Long volume,
        BigDecimal value
) {
}
