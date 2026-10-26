package com.idxintelligence.backend.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * End-of-day quote for one IDX stock. Prices are in IDR.
 * Nullable fields mean "not available", which is different from zero.
 *
 * @param tradeDate trading day the quote is from; null if unknown
 * @param value     traded value in IDR, estimated as last price x volume
 */
public record StockQuote(
        String ticker,
        String name,
        String sector,
        LocalDate tradeDate,
        BigDecimal lastPrice,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePercent,
        Long volume,
        BigDecimal value
) {

    /** Derives change, % change and value from the raw figures; any missing input leaves the result null. */
    public static StockQuote of(String ticker, String name, String sector, LocalDate tradeDate,
                                BigDecimal lastPrice, BigDecimal previousClose, Long volume) {
        BigDecimal change = lastPrice == null || previousClose == null ? null : lastPrice.subtract(previousClose);
        return new StockQuote(ticker, name, sector, tradeDate, lastPrice, previousClose, change,
                percent(change, previousClose), volume,
                lastPrice == null || volume == null ? null : lastPrice.multiply(BigDecimal.valueOf(volume)));
    }

    /** The same stock with only its last price kept, for quotes older than the market's latest day. */
    public StockQuote withoutDailyChange() {
        return new StockQuote(ticker, name, sector, tradeDate, lastPrice, null, null, null, null, null);
    }

    static BigDecimal percent(BigDecimal change, BigDecimal base) {
        if (change == null || base == null || base.signum() == 0) return null;
        return change.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }
}
