package com.idxintelligence.backend.market;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
public class MarketService {

    public static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    public static final String DEMO_NOTICE = "DEMO DATA — NOT LIVE MARKET DATA";

    private static final int LIST_SIZE = 5;

    private final MarketDataProvider provider;

    public MarketService(MarketDataProvider provider) {
        this.provider = provider;
    }

    public MarketOverview overview() {
        List<StockQuote> quotes = provider.quotes();

        List<StockQuote> gainers = quotes.stream()
                .filter(q -> q.changePercent() != null && q.changePercent().signum() > 0)
                .sorted(Comparator.comparing(StockQuote::changePercent).reversed())
                .limit(LIST_SIZE)
                .toList();

        List<StockQuote> losers = quotes.stream()
                .filter(q -> q.changePercent() != null && q.changePercent().signum() < 0)
                .sorted(Comparator.comparing(StockQuote::changePercent))
                .limit(LIST_SIZE)
                .toList();

        List<StockQuote> mostActive = quotes.stream()
                .filter(q -> q.value() != null)
                .sorted(Comparator.comparing(StockQuote::value).reversed())
                .limit(LIST_SIZE)
                .toList();

        int advancers = countBySign(quotes, 1);
        int decliners = countBySign(quotes, -1);
        int unchanged = countBySign(quotes, 0);

        return new MarketOverview(
                provider.isDemo(),
                provider.sourceName(),
                notice(),
                OffsetDateTime.now(JAKARTA),
                dataDate(provider.latestTradingDate()),
                provider.compositeIndex(),
                advancers,
                decliners,
                unchanged,
                gainers,
                losers,
                mostActive
        );
    }

    public StockDetail stock(String ticker) {
        StockQuote quote = findQuote(ticker);
        return new StockDetail(provider.isDemo(), provider.sourceName(), notice(),
                OffsetDateTime.now(JAKARTA), dataDate(quote.tradeDate()), quote);
    }

    public PriceHistory priceHistory(String ticker, PriceRange range) {
        StockQuote quote = findQuote(ticker);
        LocalDate end = provider.latestTradingDate();
        List<PriceBar> bars = provider.dailyBars(quote.ticker(), range.startFrom(end), end);
        return new PriceHistory(provider.isDemo(), provider.sourceName(), notice(),
                OffsetDateTime.now(JAKARTA), dataDate(bars.isEmpty() ? null : bars.getLast().date()),
                quote.ticker(), range.code(), bars);
    }

    private StockQuote findQuote(String ticker) {
        return provider.quote(ticker)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown ticker '" + ticker + "'"));
    }

    // Demo data is not from any real trading day, so it reports no data date.
    private LocalDate dataDate(LocalDate date) {
        return provider.isDemo() ? null : date;
    }

    private String notice() {
        return provider.isDemo() ? DEMO_NOTICE : null;
    }

    // Quotes with no change data are excluded from all three counts.
    private static int countBySign(List<StockQuote> quotes, int sign) {
        return (int) quotes.stream()
                .map(StockQuote::change)
                .filter(c -> c != null && c.signum() == sign)
                .count();
    }
}
