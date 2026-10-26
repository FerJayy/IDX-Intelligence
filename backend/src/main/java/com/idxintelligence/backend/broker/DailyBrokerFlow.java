package com.idxintelligence.backend.broker;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Per-day broker flow metrics for one stock.
 *
 * @param concentration             (top-5 net buy volume - top-5 net sell volume) / total volume.
 *                                  Positive: buying concentrated in fewer brokers than selling.
 * @param buyerCount                brokers with positive net volume that day
 * @param sellerCount               brokers with negative net volume that day
 * @param foreignNetValue           foreign investors' net value that day; null if not available
 * @param cumulativeForeignNetValue running total of foreignNetValue over the days where it is known;
 *                                  null on days where it is not available
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
