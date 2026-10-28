package com.idxintelligence.backend.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Today's closing price between the close (16:15) and the evening import, from Yahoo's 15-minute candles:
 * free and outside the IDX Edge quota. Without it a stock page would fall back to yesterday's stored close
 * until the import adds today's bar. Cached per stock for 30 minutes and shared by all viewers.
 */
@Service
public class TodayCloseService {

    private static final Logger log = LoggerFactory.getLogger(TodayCloseService.class);
    static final Duration CACHE = Duration.ofMinutes(30);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final YahooFinanceClient yahoo;
    private final JdbcClient jdbc;
    private final Clock clock;

    private record Cached(Instant fetchedAt, Optional<IdxEdgeClient.LiveQuote> quote) {
    }

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public TodayCloseService(YahooFinanceClient yahoo, JdbcClient jdbc, Clock clock) {
        this.yahoo = yahoo;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Today's close as a quote (price, change vs the last stored close, lots), or empty when not applicable. */
    public Optional<IdxEdgeClient.LiveQuote> todayClose(String ticker) {
        ZonedDateTime now = clock.instant().atZone(YahooFinanceClient.JAKARTA);
        LocalDate today = now.toLocalDate();
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY
                || !MarketSession.afterClose(now)) {
            return Optional.empty();
        }
        record Stored(LocalDate date, BigDecimal close) {
        }
        Optional<Stored> last = jdbc.sql("SELECT trade_date, close FROM daily_price WHERE ticker = ? ORDER BY trade_date DESC LIMIT 1")
                .param(ticker)
                .query((rs, n) -> new Stored(rs.getDate("trade_date").toLocalDate(), rs.getBigDecimal("close")))
                .optional();
        // Once the evening import has today's bar, the stored data is the better source.
        if (last.isEmpty() || !last.get().date().isBefore(today)) return Optional.empty();

        Cached cached = cache.get(ticker);
        if (cached != null && cached.fetchedAt().plus(CACHE).isAfter(clock.instant())
                && cached.fetchedAt().atZone(YahooFinanceClient.JAKARTA).toLocalDate().equals(today)) {
            return cached.quote();
        }
        Optional<IdxEdgeClient.LiveQuote> quote;
        try {
            Instant startOfDay = today.atStartOfDay(YahooFinanceClient.JAKARTA).toInstant();
            List<YahooFinanceClient.IntradayBar> bars = yahoo.intradayBars(ticker + ".JK", "15m", startOfDay).stream()
                    .filter(b -> !Instant.ofEpochSecond(b.time()).isBefore(startOfDay))
                    .toList();
            quote = bars.isEmpty() ? Optional.empty() : Optional.of(toQuote(ticker, bars, last.get().close()));
        } catch (RuntimeException e) {
            log.warn("Today's close for {} from Yahoo failed: {}", ticker, e.getMessage());
            quote = Optional.empty();
        }
        cache.put(ticker, new Cached(clock.instant(), quote));
        if (cache.size() > 2_000) cache.clear();
        return quote;
    }

    private static IdxEdgeClient.LiveQuote toQuote(String ticker, List<YahooFinanceClient.IntradayBar> bars,
                                                   BigDecimal previousClose) {
        YahooFinanceClient.IntradayBar lastBar = bars.getLast();
        BigDecimal close = lastBar.close();
        BigDecimal change = close.subtract(previousClose);
        BigDecimal percent = previousClose.signum() == 0 ? null
                : change.multiply(BigDecimal.valueOf(100)).divide(previousClose, 2, RoundingMode.HALF_UP);
        long shares = bars.stream().mapToLong(b -> b.volume() == null ? 0 : b.volume()).sum();
        String time = TIME.format(Instant.ofEpochSecond(lastBar.time()).atZone(YahooFinanceClient.JAKARTA));
        return new IdxEdgeClient.LiveQuote(ticker, close, change, percent,
                shares == 0 ? null : BigDecimal.valueOf(shares).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP),
                null, null, null, time, "closed", null, false);
    }
}
