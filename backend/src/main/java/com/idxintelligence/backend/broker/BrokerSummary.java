package com.idxintelligence.backend.broker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param tracked     whether broker summaries are imported for this stock at all
 * @param tradingDays trading days in the range (from price data)
 * @param brokerDays  of those, days that have broker data; fewer while the backfill is running
 */
public record BrokerSummary(
        boolean demo,
        String dataSource,
        String notice,
        OffsetDateTime generatedAt,
        String ticker,
        String range,
        boolean tracked,
        LocalDate from,
        LocalDate to,
        int tradingDays,
        int brokerDays,
        BigDecimal lastPrice,
        FlowAssessment assessment,
        List<BrokerPosition> topBuyers,
        List<BrokerPosition> topSellers,
        List<DailyBrokerFlow> daily
) {
}
