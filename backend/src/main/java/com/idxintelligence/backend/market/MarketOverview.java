package com.idxintelligence.backend.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record MarketOverview(
        boolean demo,
        String dataSource,
        String notice,
        OffsetDateTime generatedAt,
        LocalDate dataDate,
        IndexSnapshot compositeIndex,
        int advancers,
        int decliners,
        int unchanged,
        List<StockQuote> topGainers,
        List<StockQuote> topLosers,
        List<StockQuote> mostActive,
        Map<String, List<BigDecimal>> sparklines
) {
}
