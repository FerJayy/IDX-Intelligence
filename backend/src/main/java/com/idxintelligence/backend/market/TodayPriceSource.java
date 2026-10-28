package com.idxintelligence.backend.market;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Today's prices for many stocks at once, for the hours when the stored end-of-day data is still
 * yesterday's (during the session and after the close until the evening import).
 */
public interface TodayPriceSource {

    /** @param previousClose the close the change is measured from (the provider's, matching its price) */
    record TodayPrice(BigDecimal price, BigDecimal previousClose) {
    }

    /** @param asOf when the prices were fetched */
    record TodayPrices(OffsetDateTime asOf, Map<String, TodayPrice> prices) {
    }

    /** Today's prices, or empty when stored data is current (or it's not a trading day yet). */
    Optional<TodayPrices> todayPrices();
}
