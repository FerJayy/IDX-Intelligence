package com.idxintelligence.backend.market;

import java.time.OffsetDateTime;
import java.util.List;

public record PriceHistory(
        boolean demo,
        String dataSource,
        String notice,
        OffsetDateTime generatedAt,
        String ticker,
        String range,
        List<PriceBar> bars
) {
}
