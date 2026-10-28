package com.idxintelligence.backend.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time prices for the stock someone is looking at. Each provider request comes out of the same
 * daily quota as broker data, so: only during market hours, one request per stock per minute (shared
 * by everyone viewing it), and a separate daily budget so browsing never eats the broker import.
 */
@Service
public class LivePriceService {

    public enum Status { LIVE, MARKET_CLOSED, BUDGET_USED, UNAVAILABLE }

    static final Duration CACHE = Duration.ofSeconds(60);
    // IDX: pre-opening from 08:45, session 1 from 09:00, pre-closing/post-trading until ~16:15 WIB.
    static final LocalTime OPENS = LocalTime.of(8, 55);
    static final LocalTime CLOSES = LocalTime.of(16, 15);

    private final IdxEdgeClient client;
    private final Clock clock;
    private final int dailyBudget;

    private record Cached(Instant fetchedAt, IdxEdgeClient.LiveQuote quote) {
    }

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private LocalDate budgetDay;
    private int usedToday;
    /** A weekday the provider reported as closed (an exchange holiday): no more requests that day. */
    private volatile LocalDate closedDay;

    public LivePriceService(IdxEdgeClient client, Clock clock,
                            @Value("${live.daily-budget:150}") int dailyBudget) {
        this.client = client;
        this.clock = clock;
        this.dailyBudget = dailyBudget;
    }

    /** @param budgetLeft requests left in today's live budget */
    public record LiveResult(Status status, IdxEdgeClient.LiveQuote quote, Instant fetchedAt, int budgetLeft) {
    }

    public LiveResult quote(String ticker) {
        String code = ticker.toUpperCase();
        ZonedDateTime now = clock.instant().atZone(YahooFinanceClient.JAKARTA);
        if (!client.isConfigured()) return new LiveResult(Status.UNAVAILABLE, null, null, budgetLeft(now));
        if (!marketHours(now) || now.toLocalDate().equals(closedDay)) {
            return new LiveResult(Status.MARKET_CLOSED, null, null, budgetLeft(now));
        }

        Cached cached = cache.get(code);
        boolean cachedToday = cached != null
                && cached.fetchedAt().atZone(YahooFinanceClient.JAKARTA).toLocalDate().equals(now.toLocalDate());
        // Prices don't move during the lunch break: today's last quote stands until the session resumes.
        if (cached != null && (cached.fetchedAt().plus(CACHE).isAfter(clock.instant())
                || (cachedToday && MarketSession.lunchBreak(now)))) {
            return new LiveResult(Status.LIVE, cached.quote(), cached.fetchedAt(), budgetLeft(now));
        }
        if (!takeFromBudget(now)) {
            return new LiveResult(Status.BUDGET_USED, null, null, 0);
        }
        try {
            IdxEdgeClient.LiveQuote quote = client.livePrice(code);
            if (MarketSession.continuousTrading(now) && MarketSession.saysClosed(quote.marketState(), quote.marketLabel())) {
                closedDay = now.toLocalDate();
                return new LiveResult(Status.MARKET_CLOSED, null, null, budgetLeft(now));
            }
            // A price beyond IDX's daily limit is a negotiated-market trade or bad data, not the market price.
            if (!LiveMoversService.withinDailyLimit(quote.lastPrice(), quote.change(), quote.changePercent())) {
                return new LiveResult(Status.UNAVAILABLE, null, null, budgetLeft(now));
            }
            Instant fetchedAt = clock.instant();
            cache.put(code, new Cached(fetchedAt, quote));
            return new LiveResult(Status.LIVE, quote, fetchedAt, budgetLeft(now));
        } catch (RuntimeException e) {
            return new LiveResult(Status.UNAVAILABLE, null, null, budgetLeft(now));
        }
    }

    /** Weekdays within trading hours; holidays aren't known here (the provider's market label shows them). */
    static boolean marketHours(ZonedDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !time.isBefore(OPENS) && time.isBefore(CLOSES);
    }

    private synchronized boolean takeFromBudget(ZonedDateTime now) {
        resetIfNewDay(now);
        if (usedToday >= dailyBudget) return false;
        usedToday++;
        return true;
    }

    private synchronized int budgetLeft(ZonedDateTime now) {
        resetIfNewDay(now);
        return dailyBudget - usedToday;
    }

    private void resetIfNewDay(ZonedDateTime now) {
        if (!now.toLocalDate().equals(budgetDay)) {
            budgetDay = now.toLocalDate();
            usedToday = 0;
        }
    }
}
