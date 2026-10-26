package com.idxintelligence.backend.market;

import java.time.OffsetDateTime;

public record StockDetail(
        boolean demo,
        String dataSource,
        String notice,
        OffsetDateTime generatedAt,
        StockQuote quote
) {
}
