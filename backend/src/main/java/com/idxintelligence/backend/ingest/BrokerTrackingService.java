package com.idxintelligence.backend.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Decides which stocks get broker summaries. Each tracked stock costs ~2 provider requests a day
 * and ~2.7 MB of storage a year, so new ones are capped in total and per day, and only get about
 * a month of history.
 */
@Service
public class BrokerTrackingService {

    public enum Reason { CORE, SCREENER, USER }

    public enum Outcome {
        TRACKED, ALREADY_TRACKED, UNKNOWN_STOCK, TOTAL_LIMIT_REACHED, DAILY_LIMIT_REACHED
    }

    /** Calendar days of history fetched for a newly tracked stock (~20-25 trading days). */
    static final int NEW_STOCK_HISTORY_DAYS = 35;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final int maxTracked;
    private final int maxPerDayScreener;
    private final int maxPerDayUser;

    public BrokerTrackingService(JdbcClient jdbc, Clock clock,
                                 @Value("${tracking.max-stocks:80}") int maxTracked,
                                 @Value("${tracking.max-new-per-day.screener:5}") int maxPerDayScreener,
                                 @Value("${tracking.max-new-per-day.user:3}") int maxPerDayUser) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.maxTracked = maxTracked;
        this.maxPerDayScreener = maxPerDayScreener;
        this.maxPerDayUser = maxPerDayUser;
    }

    public Outcome track(String ticker, Reason reason) {
        LocalDate today = LocalDate.now(clock.withZone(YahooFinanceClient.JAKARTA));
        Boolean tracked = jdbc.sql("SELECT broker_tracked FROM stock WHERE ticker = ? AND active")
                .param(ticker.toUpperCase())
                .query(Boolean.class)
                .optional()
                .orElse(null);
        if (tracked == null) return Outcome.UNKNOWN_STOCK;
        if (tracked) return Outcome.ALREADY_TRACKED;

        int total = jdbc.sql("SELECT count(*) FROM stock WHERE broker_tracked").query(Integer.class).single();
        if (total >= maxTracked) return Outcome.TOTAL_LIMIT_REACHED;
        int addedToday = jdbc.sql("SELECT count(*) FROM stock WHERE broker_tracked AND broker_tracked_since = ? AND broker_tracked_reason = ?")
                .params(Date.valueOf(today), reason.name())
                .query(Integer.class)
                .single();
        if (addedToday >= (reason == Reason.USER ? maxPerDayUser : maxPerDayScreener)) return Outcome.DAILY_LIMIT_REACHED;

        jdbc.sql("""
                UPDATE stock SET broker_tracked = true, broker_tracked_reason = ?, broker_tracked_since = ?,
                                 broker_backfill_from = ?
                WHERE ticker = ? AND NOT broker_tracked
                """)
                .params(reason.name(), Date.valueOf(today), Date.valueOf(today.minusDays(NEW_STOCK_HISTORY_DAYS)),
                        ticker.toUpperCase())
                .update();
        return Outcome.TRACKED;
    }
}
