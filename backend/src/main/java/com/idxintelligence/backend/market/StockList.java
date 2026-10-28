package com.idxintelligence.backend.market;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * One page of the stock list.
 *
 * @param todayPricesAsOf set when prices/changes are today's (Yahoo, before the evening import); null = stored data
 * @param total   stocks matching the search and filter (all pages)
 * @param sectors all known sectors, for the filter menu
 */
public record StockList(
        boolean demo,
        String dataSource,
        String notice,
        OffsetDateTime generatedAt,
        LocalDate dataDate,
        OffsetDateTime todayPricesAsOf,
        int total,
        int page,
        int size,
        List<String> sectors,
        List<StockQuote> stocks
) {
}
