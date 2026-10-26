package com.idxintelligence.backend.market;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Source of market data. The mock implementation is used until a real
 * provider is chosen; a real one will implement this same interface.
 */
public interface MarketDataProvider {

    /** Short name shown to users as the data source, e.g. "DEMO". */
    String sourceName();

    boolean isDemo();

    IndexSnapshot compositeIndex();

    List<StockQuote> quotes();

    default Optional<StockQuote> quote(String ticker) {
        return quotes().stream()
                .filter(q -> q.ticker().equalsIgnoreCase(ticker))
                .findFirst();
    }

    /** Daily bars for {@code ticker} between {@code from} and {@code to} inclusive, oldest first. */
    List<PriceBar> dailyBars(String ticker, LocalDate from, LocalDate to);

    /** Date of the most recent trading day the provider has data for. */
    LocalDate latestTradingDate();
}
