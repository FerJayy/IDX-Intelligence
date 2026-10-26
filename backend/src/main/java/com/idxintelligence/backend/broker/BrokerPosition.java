package com.idxintelligence.backend.broker;

import java.math.BigDecimal;

/**
 * One broker's activity in one stock, totalled over a period. Volumes in shares, values in IDR.
 *
 * @param brokerName       null if unknown
 * @param foreignNetValue  foreign investors' net value through this broker; null if not available for every day
 */
public record BrokerPosition(
        String brokerCode,
        String brokerName,
        long buyVolume,
        BigDecimal buyValue,
        BigDecimal buyAveragePrice,
        long sellVolume,
        BigDecimal sellValue,
        BigDecimal sellAveragePrice,
        long netVolume,
        BigDecimal netValue,
        BigDecimal foreignNetValue
) {
}
