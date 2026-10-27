package com.idxintelligence.backend.market;

import com.idxintelligence.backend.ingest.YahooFinanceClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Chart candles for stocks and the IHSG. Daily candles come from the database; weekly and monthly
 * ones are built from them; 15m/1h come from Yahoo on request (not stored, kept in memory for a few
 * minutes so page views don't hit Yahoo each time); 4h is built from 1h.
 */
@Service
public class CandleService {

    static final Duration INTRADAY_CACHE = Duration.ofMinutes(10);
    private static final int CACHE_ENTRIES = 300;
    /** Our index code -> Yahoo symbol. */
    private static final Map<String, String> INDEX_SYMBOLS = Map.of("COMPOSITE", "^JKSE");

    private final MarketDataProvider provider;
    private final MarketService marketService;
    private final YahooFinanceClient yahoo;
    private final Clock clock;

    private record Cached(Instant fetchedAt, List<YahooFinanceClient.IntradayBar> bars) {
    }

    // Small LRU cache: symbol|interval|range -> candles.
    private final Map<String, Cached> intradayCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > CACHE_ENTRIES;
        }
    };

    public CandleService(MarketDataProvider provider, MarketService marketService, YahooFinanceClient yahoo, Clock clock) {
        this.provider = provider;
        this.marketService = marketService;
        this.yahoo = yahoo;
        this.clock = clock;
    }

    public CandleSeries stock(String ticker, ChartInterval interval, String rangeCode) {
        StockQuote quote = marketService.stock(ticker).quote(); // 404 for unknown tickers
        ChartInterval.Range range = interval.range(rangeCode);
        return series(quote.ticker(), interval, range, quote.ticker() + ".JK",
                (from) -> provider.dailyBars(quote.ticker(), from, provider.latestTradingDate()));
    }

    public CandleSeries index(String code, ChartInterval interval, String rangeCode) {
        String upper = code.toUpperCase();
        String symbol = INDEX_SYMBOLS.get(upper);
        if (symbol == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown index '" + code + "'");
        }
        ChartInterval.Range range = interval.range(rangeCode);
        return series(upper, interval, range, symbol,
                (from) -> provider.indexBars(upper, from, provider.latestTradingDate()));
    }

    private CandleSeries series(String name, ChartInterval interval, ChartInterval.Range range, String yahooSymbol,
                                Function<LocalDate, List<PriceBar>> daily) {
        boolean adjusted = interval.adjusted(range);
        List<Candle> candles;
        if (interval.intraday()) {
            // Demo mode has no intraday source; real mode asks Yahoo.
            candles = provider.isDemo() ? List.of() : intraday(yahooSymbol, interval, range);
        } else {
            List<PriceBar> bars = daily.apply(range.startFrom(provider.latestTradingDate()));
            List<Candle> days = bars.stream().map(b -> dailyCandle(b, adjusted)).toList();
            candles = switch (interval) {
                case W1 -> group(bars, days, d -> d.with(DayOfWeek.MONDAY));
                case MO1 -> group(bars, days, d -> YearMonth.from(d).atDay(1));
                default -> days;
            };
        }
        return new CandleSeries(provider.isDemo(), interval.intraday() ? "Yahoo Finance" : provider.sourceName(),
                provider.isDemo() ? MarketService.DEMO_NOTICE : null, OffsetDateTime.now(MarketService.JAKARTA),
                name, interval.code(), range.code(), interval.ranges().stream().map(ChartInterval.Range::code).toList(),
                interval.intraday(), adjusted && !interval.intraday(), candles);
    }

    private List<Candle> intraday(String symbol, ChartInterval interval, ChartInterval.Range range) {
        String yahooInterval = interval == ChartInterval.M15 ? "15m" : "60m";
        LocalDate today = LocalDate.now(clock.withZone(MarketService.JAKARTA));
        Instant from = range.startFrom(today).atStartOfDay(MarketService.JAKARTA).toInstant();
        String key = symbol + "|" + yahooInterval + "|" + range.code();

        List<YahooFinanceClient.IntradayBar> bars;
        synchronized (intradayCache) {
            Cached cached = intradayCache.get(key);
            if (cached != null && cached.fetchedAt().plus(INTRADAY_CACHE).isAfter(clock.instant())) {
                bars = cached.bars();
            } else {
                bars = null;
            }
        }
        if (bars == null) {
            bars = yahoo.intradayBars(symbol, yahooInterval, from);
            synchronized (intradayCache) {
                intradayCache.put(key, new Cached(clock.instant(), bars));
            }
        }

        List<Candle> candles = bars.stream()
                .map(b -> new Candle(b.time(), b.open(), b.high(), b.low(), b.close(), b.volume()))
                .toList();
        return interval == ChartInterval.H4 ? fourHour(candles) : candles;
    }

    /**
     * IDX trades 09:00-12:00 and 13:30-15:50 WIB, so "4h" is one morning candle (09:00) and one
     * afternoon candle (13:00) per day.
     */
    static List<Candle> fourHour(List<Candle> hourly) {
        Map<Long, List<Candle>> buckets = new LinkedHashMap<>();
        for (Candle c : hourly) {
            ZonedDateTime start = Instant.ofEpochSecond(c.time()).atZone(MarketService.JAKARTA);
            ZonedDateTime bucket = start.toLocalDate().atTime(start.getHour() < 13 ? 9 : 13, 0).atZone(MarketService.JAKARTA);
            buckets.computeIfAbsent(bucket.toEpochSecond(), k -> new ArrayList<>()).add(c);
        }
        List<Candle> result = new ArrayList<>();
        buckets.forEach((time, parts) -> result.add(merge(time, parts)));
        return result;
    }

    private static List<Candle> group(List<PriceBar> bars, List<Candle> days, Function<LocalDate, LocalDate> period) {
        Map<LocalDate, List<Candle>> groups = new LinkedHashMap<>();
        for (int i = 0; i < bars.size(); i++) {
            groups.computeIfAbsent(period.apply(bars.get(i).date()), k -> new ArrayList<>()).add(days.get(i));
        }
        List<Candle> result = new ArrayList<>();
        // Each candle is stamped with its first trading day, so the date shown is a real session.
        groups.values().forEach(parts -> result.add(merge(parts.getFirst().time(), parts)));
        return result;
    }

    /** Combines candles: first open, highest high, lowest low, last close, summed volume. Missing stays missing. */
    static Candle merge(long time, List<Candle> parts) {
        BigDecimal high = null;
        BigDecimal low = null;
        long volume = 0;
        boolean anyVolume = false;
        for (Candle c : parts) {
            BigDecimal h = c.high() != null ? c.high() : c.close();
            BigDecimal l = c.low() != null ? c.low() : c.close();
            high = high == null || h.compareTo(high) > 0 ? h : high;
            low = low == null || l.compareTo(low) < 0 ? l : low;
            if (c.volume() != null) {
                volume += c.volume();
                anyVolume = true;
            }
        }
        return new Candle(time, parts.getFirst().open(), high, low, parts.getLast().close(), anyVolume ? volume : null);
    }

    private static Candle dailyCandle(PriceBar bar, boolean adjusted) {
        BigDecimal factor = adjusted && bar.adjustedClose() != null && bar.close().signum() != 0
                ? bar.adjustedClose().divide(bar.close(), 10, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        return new Candle(bar.date().atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
                scale(bar.open(), factor), scale(bar.high(), factor), scale(bar.low(), factor),
                scale(bar.close(), factor), bar.volume());
    }

    private static BigDecimal scale(BigDecimal value, BigDecimal factor) {
        if (value == null) return null;
        return factor.compareTo(BigDecimal.ONE) == 0 ? value : value.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
