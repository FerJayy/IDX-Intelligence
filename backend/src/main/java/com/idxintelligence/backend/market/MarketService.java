package com.idxintelligence.backend.market;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
public class MarketService {

    public static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    public static final String DEMO_NOTICE = "DEMO DATA — NOT LIVE MARKET DATA";

    private static final int LIST_SIZE = 10;
    private static final int SPARKLINE_DAYS = 20;
    /** Minimum traded value (IDR) for a stock to count as a top gainer or loser. */
    static final BigDecimal MIN_MOVER_VALUE = new BigDecimal("1000000000");
    static final int MAX_PAGE_SIZE = 200;

    private final MarketDataProvider provider;

    public MarketService(MarketDataProvider provider) {
        this.provider = provider;
    }

    public MarketOverview overview() {
        List<StockQuote> quotes = provider.quotes();

        // Gainers/losers ignore thinly traded stocks: a few lots can move them 20%+, which says little.
        List<StockQuote> movers = quotes.stream().filter(MarketService::liquidEnough).toList();

        List<StockQuote> gainers = movers.stream()
                .filter(q -> q.changePercent() != null && q.changePercent().signum() > 0)
                .sorted(Comparator.comparing(StockQuote::changePercent).reversed())
                .limit(LIST_SIZE)
                .toList();

        List<StockQuote> losers = movers.stream()
                .filter(q -> q.changePercent() != null && q.changePercent().signum() < 0)
                .sorted(Comparator.comparing(StockQuote::changePercent))
                .limit(LIST_SIZE)
                .toList();

        List<StockQuote> mostActive = quotes.stream()
                .filter(q -> q.value() != null)
                .sorted(Comparator.comparing(StockQuote::value).reversed())
                .limit(LIST_SIZE)
                .toList();

        List<String> shown = java.util.stream.Stream.of(gainers, losers, mostActive)
                .flatMap(List::stream).map(StockQuote::ticker).distinct().toList();
        Map<String, List<BigDecimal>> sparklines = provider.recentCloses(shown, SPARKLINE_DAYS);

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
                mostActive,
                sparklines
        );
    }

    /**
     * Searchable, sortable list of all stocks. Sort keys: ticker, price, change, volume, value, marketCap.
     * Missing values always sort last, whatever the direction.
     */
    public StockList list(String search, String sector, String sort, boolean ascending, int page, int size) {
        return list(search, sector, null, null, sort, ascending, page, size);
    }

    public StockList list(String search, String sector, String change, String sort, boolean ascending, int page, int size) {
        return list(search, sector, change, null, sort, ascending, page, size);
    }

    /**
     * @param change "up", "down" or "flat" to keep only stocks that moved that way on the latest day
     * @param hsc    "only" or "exclude" for stocks on IDX's High Shareholding Concentration list
     */
    public StockList list(String search, String sector, String change, String hsc, String sort, boolean ascending,
                          int page, int size) {
        List<StockQuote> quotes = provider.quotes();
        List<String> sectors = quotes.stream().map(StockQuote::sector).filter(Objects::nonNull)
                .distinct().sorted().toList();

        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<StockQuote> matching = quotes.stream()
                .filter(q -> needle.isEmpty()
                        || q.ticker().toLowerCase(Locale.ROOT).contains(needle)
                        || (q.name() != null && q.name().toLowerCase(Locale.ROOT).contains(needle)))
                .filter(q -> sector == null || sector.isBlank() || sector.equals(q.sector()))
                .filter(q -> change == null || change.isBlank() || matchesChange(q, change))
                .filter(q -> hsc == null || hsc.isBlank() || matchesHsc(q, hsc))
                .sorted(comparator(sort, ascending))
                .toList();

        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int pageIndex = Math.max(1, page);
        int fromIndex = Math.min((pageIndex - 1) * pageSize, matching.size());
        List<StockQuote> pageItems = matching.subList(fromIndex, Math.min(fromIndex + pageSize, matching.size()));

        return new StockList(provider.isDemo(), provider.sourceName(), notice(), OffsetDateTime.now(JAKARTA),
                dataDate(provider.latestTradingDate()), matching.size(), pageIndex, pageSize, sectors, pageItems);
    }

    private static Comparator<StockQuote> comparator(String sort, boolean ascending) {
        if (sort == null || sort.isBlank() || sort.equals("ticker")) {
            Comparator<StockQuote> byTicker = Comparator.comparing(StockQuote::ticker);
            return ascending ? byTicker : byTicker.reversed();
        }
        Function<StockQuote, BigDecimal> key = switch (sort) {
            case "price" -> StockQuote::lastPrice;
            case "change" -> StockQuote::changePercent;
            case "volume" -> q -> q.volume() == null ? null : BigDecimal.valueOf(q.volume());
            case "value" -> StockQuote::value;
            case "marketCap" -> StockQuote::marketCap;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown sort '" + sort + "'. Use ticker, price, change, volume, value or marketCap.");
        };
        Comparator<BigDecimal> direction = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing(key, Comparator.nullsLast(direction)).thenComparing(StockQuote::ticker);
    }

    private static boolean matchesHsc(StockQuote q, String hsc) {
        return switch (hsc) {
            case "only" -> q.hsc();
            case "exclude" -> !q.hsc();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hsc must be only or exclude");
        };
    }

    private static boolean matchesChange(StockQuote q, String change) {
        if (q.change() == null) return false;
        return switch (change) {
            case "up" -> q.change().signum() > 0;
            case "down" -> q.change().signum() < 0;
            case "flat" -> q.change().signum() == 0;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "change must be up, down or flat");
        };
    }

    private static boolean liquidEnough(StockQuote q) {
        return q.value() != null && q.value().compareTo(MIN_MOVER_VALUE) >= 0;
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
