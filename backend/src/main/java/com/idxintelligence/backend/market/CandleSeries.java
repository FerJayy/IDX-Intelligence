package com.idxintelligence.backend.market;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Candles for one stock or index.
 *
 * @param symbol         stock ticker, or the index code (COMPOSITE)
 * @param ranges         ranges available for this interval, for the chart's buttons
 * @param adjusted       prices adjusted for splits and dividends (long views)
 * @param intraday       intraday candles: fetched live from the source and possibly delayed
 */
public record CandleSeries(
        boolean demo,
        String dataSource,
        String notice,
        OffsetDateTime generatedAt,
        String symbol,
        String interval,
        String range,
        List<String> ranges,
        boolean intraday,
        boolean adjusted,
        List<Candle> candles
) {
}
