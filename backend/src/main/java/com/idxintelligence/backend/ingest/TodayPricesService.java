package com.idxintelligence.backend.ingest;

import com.idxintelligence.backend.market.TodayPriceSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Today's prices for every listed stock from Yahoo's batch ("spark") endpoint: 20 stocks per request,
 * ~50 requests for the whole market, free and outside the IDX Edge quota. Used from 09:00 until the
 * evening import has stored today's bars, so the stock list never shows yesterday's close as current.
 * One shared snapshot: at most every 10 minutes in the session and every 30 minutes after the close,
 * none during the lunch break. The first request of the day waits for it; later ones refresh in the
 * background and are served the previous snapshot meanwhile.
 */
@Service
public class TodayPricesService implements TodayPriceSource {

    private static final Logger log = LoggerFactory.getLogger(TodayPricesService.class);
    static final Duration REFRESH_IN_SESSION = Duration.ofMinutes(10);
    static final Duration REFRESH_AFTER_CLOSE = Duration.ofMinutes(30);
    /** Once this share of active stocks has today's stored bar, the import is done and the snapshot isn't needed. */
    static final double IMPORTED_SHARE = 0.8;
    /** Yahoo batches fetched at the same time. */
    static final int PARALLEL = 6;
    private volatile LocalDate importedDay;
    private volatile Instant importedCheckedAt;

    private final YahooFinanceClient yahoo;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile TodayPrices snapshot;

    public TodayPricesService(YahooFinanceClient yahoo, JdbcClient jdbc, Clock clock) {
        this.yahoo = yahoo;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<TodayPrices> todayPrices() {
        ZonedDateTime now = clock.instant().atZone(YahooFinanceClient.JAKARTA);
        LocalDate today = now.toLocalDate();
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY
                || now.toLocalTime().isBefore(LocalTime.of(9, 0)) || importedToday(today)) {
            return Optional.empty();
        }
        TodayPrices current = snapshot;
        boolean fromToday = current != null && current.asOf().toLocalDate().equals(today);
        if (!fromToday) {
            refresh(now);                     // first of the day: wait for it
            current = snapshot;
            return current != null && current.asOf().toLocalDate().equals(today) ? Optional.of(current) : Optional.empty();
        }
        Duration maxAge = MarketSession.afterClose(now) ? REFRESH_AFTER_CLOSE : REFRESH_IN_SESSION;
        boolean stale = current.asOf().toInstant().plus(maxAge).isBefore(clock.instant());
        // Prices don't change in the lunch break, and the snapshot taken after the close is final.
        boolean frozen = MarketSession.lunchBreak(now)
                || (MarketSession.afterClose(now) && MarketSession.afterClose(current.asOf().atZoneSameInstant(YahooFinanceClient.JAKARTA)));
        if (stale && !frozen) {
            Thread.ofVirtual().start(() -> refresh(clock.instant().atZone(YahooFinanceClient.JAKARTA)));
        }
        return Optional.of(current);
    }

    /** Checked at most every 2 minutes (it runs on every stock-list request). */
    private boolean importedToday(LocalDate today) {
        if (today.equals(importedDay)) return true;
        if (importedCheckedAt != null && importedCheckedAt.plus(Duration.ofMinutes(2)).isAfter(clock.instant())) return false;
        importedCheckedAt = clock.instant();
        boolean done = jdbc.sql("""
                SELECT count(*) FILTER (WHERE p.trade_date = ?)::float / greatest(count(*), 1)
                FROM stock s LEFT JOIN LATERAL (
                    SELECT trade_date FROM daily_price d WHERE d.ticker = s.ticker ORDER BY trade_date DESC LIMIT 1
                ) p ON true
                WHERE s.active AND s.price_missing_since IS NULL
                """)
                .param(Date.valueOf(today))
                .query(Double.class).single() >= IMPORTED_SHARE;
        if (done) importedDay = today;
        return done;
    }

    private void refresh(ZonedDateTime now) {
        if (!refreshing.compareAndSet(false, true)) return;   // another request is already fetching
        try {
            List<String> tickers = jdbc.sql("SELECT ticker FROM stock WHERE active AND price_missing_since IS NULL ORDER BY ticker")
                    .query(String.class).list();
            LocalDate today = now.toLocalDate();
            Map<String, TodayPrice> prices = new ConcurrentHashMap<>();
            Semaphore parallel = new Semaphore(PARALLEL);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < tickers.size(); i += YahooFinanceClient.SPARK_BATCH) {
                    List<String> batch = tickers.subList(i, Math.min(i + YahooFinanceClient.SPARK_BATCH, tickers.size()));
                    executor.submit(() -> {
                        parallel.acquireUninterruptibly();
                        try {
                            fetch(batch, today, prices);
                        } finally {
                            parallel.release();
                        }
                    });
                }
            }
            if (!prices.isEmpty()) {
                snapshot = new TodayPrices(clock.instant().atZone(YahooFinanceClient.JAKARTA).toOffsetDateTime(), Map.copyOf(prices));
                log.info("Today's prices: {} of {} stocks", prices.size(), tickers.size());
            }
        } finally {
            refreshing.set(false);
        }
    }

    private void fetch(List<String> batch, LocalDate today, Map<String, TodayPrice> into) {
        try {
            yahoo.spark(batch.stream().map(t -> t + ".JK").toList()).forEach((symbol, q) -> {
                // Only prices from today: a suspended or untraded stock keeps its stored close.
                if (!Instant.ofEpochSecond(q.time()).atZone(YahooFinanceClient.JAKARTA).toLocalDate().equals(today)) return;
                BigDecimal change = q.price().subtract(q.previousClose());
                BigDecimal percent = q.previousClose().signum() == 0 ? null
                        : change.multiply(BigDecimal.valueOf(100)).divide(q.previousClose(), 2, RoundingMode.HALF_UP);
                if (!LiveMoversService.withinDailyLimit(q.price(), change, percent)) return;
                into.put(symbol.replace(".JK", ""), new TodayPrice(q.price(), q.previousClose()));
            });
        } catch (RuntimeException e) {
            log.warn("Today's prices: a Yahoo batch failed ({}), continuing", e.getMessage());
        }
    }
}
