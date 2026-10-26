package com.idxintelligence.backend.broker;

import java.math.BigDecimal;

/** One broker's activity in one stock, totalled over a period. Volumes in shares, values in IDR. */
public record BrokerPosition(
        String brokerCode,
        BrokerType brokerType,
        long buyVolume,
        BigDecimal buyValue,
        BigDecimal buyAveragePrice,
        long sellVolume,
        BigDecimal sellValue,
        BigDecimal sellAveragePrice,
        long netVolume,
        BigDecimal netValue
) {
}
