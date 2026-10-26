package com.idxintelligence.backend.broker;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of an IDX broker summary: what one broker bought and sold in one
 * stock on one trading day. Volumes are in shares, values in IDR.
 *
 * @param brokerName        null if unknown
 * @param foreignBuyValue   part of buyValue done by foreign investors; null = not available
 * @param foreignSellValue  part of sellValue done by foreign investors; null = not available
 */
public record BrokerDailyActivity(
        LocalDate date,
        String brokerCode,
        String brokerName,
        long buyVolume,
        BigDecimal buyValue,
        long sellVolume,
        BigDecimal sellValue,
        BigDecimal foreignBuyValue,
        BigDecimal foreignSellValue
) {
    public long netVolume() {
        return buyVolume - sellVolume;
    }

    public BigDecimal netValue() {
        return buyValue.subtract(sellValue);
    }

    /** Foreign investors' net value through this broker, or null if not available. */
    public BigDecimal foreignNetValue() {
        return foreignBuyValue == null || foreignSellValue == null ? null : foreignBuyValue.subtract(foreignSellValue);
    }
}
