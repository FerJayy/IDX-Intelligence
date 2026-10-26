package com.idxintelligence.backend.signal;

import java.math.BigDecimal;

/**
 * Recent broker flow for one stock, ending on the day being scored.
 *
 * @param days             broker days used (most recent first, ending on the scored day)
 * @param avgConcentration average top-5 concentration over those days (fraction of volume)
 * @param foreignNetValue  foreign investors' net value over those days; null if not known for all of them
 * @param topBuyerCode     broker with the largest net buy over those days; null if none
 * @param topBuyerName     its name, if known
 * @param topBuyerAvgPrice its average buy price over those days (unadjusted, as traded); null if none
 */
record BrokerSignal(int days, double avgConcentration, BigDecimal foreignNetValue,
                    String topBuyerCode, String topBuyerName, BigDecimal topBuyerAvgPrice) {
}
