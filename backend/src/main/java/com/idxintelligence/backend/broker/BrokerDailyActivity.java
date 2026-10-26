package com.idxintelligence.backend.broker;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of an IDX broker summary: what one broker bought and sold in one
 * stock on one trading day. Volumes are in shares, values in IDR.
 */
public record BrokerDailyActivity(
        LocalDate date,
        String brokerCode,
        BrokerType brokerType,
        long buyVolume,
        BigDecimal buyValue,
        long sellVolume,
        BigDecimal sellValue
) {
    public long netVolume() {
        return buyVolume - sellVolume;
    }

    public BigDecimal netValue() {
        return buyValue.subtract(sellValue);
    }
}
