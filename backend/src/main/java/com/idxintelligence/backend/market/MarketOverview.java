package com.idxintelligence.backend.market;

import java.time.OffsetDateTime;
import java.util.List;

public record MarketOverview(
        boolean demo,
        String dataSource,
        String notice,
        OffsetDateTime generatedAt,
        IndexSnapshot compositeIndex,
        int advancers,
        int decliners,
        int unchanged,
        List<StockQuote> topGainers,
        List<StockQuote> topLosers,
        List<StockQuote> mostActive
) {
}
