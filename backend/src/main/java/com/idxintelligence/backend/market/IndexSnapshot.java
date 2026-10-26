package com.idxintelligence.backend.market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IndexSnapshot(
        String code,
        String name,
        LocalDate tradeDate,
        BigDecimal value,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePercent
) {

    public static IndexSnapshot of(String code, String name, LocalDate tradeDate,
                                   BigDecimal value, BigDecimal previousClose) {
        BigDecimal change = value == null || previousClose == null ? null : value.subtract(previousClose);
        return new IndexSnapshot(code, name, tradeDate, value, previousClose, change,
                StockQuote.percent(change, previousClose));
    }
}
