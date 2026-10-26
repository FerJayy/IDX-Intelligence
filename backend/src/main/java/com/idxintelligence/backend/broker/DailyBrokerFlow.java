package com.idxintelligence.backend.broker;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Per-day broker flow metrics for one stock.
 *
 * @param concentration (top-5 net buy volume - top-5 net sell volume) / total volume.
 *                      Positive: buying concentrated in fewer brokers than selling.
 * @param buyerCount    brokers with positive net volume that day
 * @param sellerCount   brokers with negative net volume that day
 */
public record DailyBrokerFlow(
        LocalDate date,
        long totalVolume,
        long top5NetBuyVolume,
        long top5NetSellVolume,
        BigDecimal concentration,
        int buyerCount,
        int sellerCount,
        BigDecimal foreignNetValue,
        BigDecimal cumulativeForeignNetValue
) {
}
