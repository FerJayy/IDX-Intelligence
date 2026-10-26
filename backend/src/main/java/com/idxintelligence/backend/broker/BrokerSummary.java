package com.idxintelligence.backend.broker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record BrokerSummary(
        boolean demo,
        String dataSource,
        String notice,
        OffsetDateTime generatedAt,
        String ticker,
        String range,
        LocalDate from,
        LocalDate to,
        BigDecimal lastPrice,
        FlowAssessment assessment,
        List<BrokerPosition> topBuyers,
        List<BrokerPosition> topSellers,
        List<DailyBrokerFlow> daily
) {
}
