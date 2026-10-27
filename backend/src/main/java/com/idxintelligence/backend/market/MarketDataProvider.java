package com.idxintelligence.backend.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    /** Daily values of a market index (e.g. COMPOSITE = IHSG), oldest first; empty if unavailable. */
    default List<PriceBar> indexBars(String code, LocalDate from, LocalDate to) {
        return List.of();
    }

    /** Last {@code days} closes per ticker, oldest first, for sparklines. */
    default Map<String, List<BigDecimal>> recentCloses(List<String> tickers, int days) {
        LocalDate end = latestTradingDate();
        Map<String, List<BigDecimal>> result = new java.util.LinkedHashMap<>();
        for (String ticker : tickers) {
            List<PriceBar> bars = dailyBars(ticker, end.minusDays(days * 2L + 10), end);
            result.put(ticker, bars.subList(Math.max(0, bars.size() - days), bars.size()).stream().map(PriceBar::close).toList());
        }
        return result;
    }

    /** Date of the most recent trading day the provider has data for. */
    LocalDate latestTradingDate();
}
