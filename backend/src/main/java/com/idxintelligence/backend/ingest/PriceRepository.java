package com.idxintelligence.backend.ingest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class PriceRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public PriceRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Every listed (active) stock: prices are imported for all of them. */
    public List<String> trackedTickers() {
        return jdbc.sql("SELECT ticker FROM stock WHERE active ORDER BY ticker").query(String.class).list();
    }

    /**
     * Active stocks to import on {@code today}: those Yahoo had no data for are only retried once a
     * week after they went missing, so they don't fail every day.
     */
    public List<String> importableTickers(LocalDate today) {
        return jdbc.sql("""
                SELECT ticker FROM stock
                WHERE active AND (price_missing_since IS NULL OR (? - price_missing_since) % 7 = 0)
                ORDER BY ticker
                """)
                .param(Date.valueOf(today))
                .query(String.class)
                .list();
    }

    /** Remembers the first day Yahoo had no data for this stock (kept if already set). */
    public void markPriceMissing(String ticker, LocalDate today) {
        jdbc.sql("UPDATE stock SET price_missing_since = coalesce(price_missing_since, ?) WHERE ticker = ?")
                .params(Date.valueOf(today), ticker)
                .update();
    }

    public void clearPriceMissing(String ticker) {
        jdbc.sql("UPDATE stock SET price_missing_since = NULL WHERE ticker = ? AND price_missing_since IS NOT NULL")
                .param(ticker)
                .update();
    }

    /** Most recent stored trading day for {@code ticker}, or null if none yet. */
    public LocalDate latestTradeDate(String ticker) {
        return jdbc.sql("SELECT max(trade_date) FROM daily_price WHERE ticker = ?")
                .param(ticker)
                .query(LocalDate.class)
                .optional()
                .orElse(null);
    }

    /**
     * Inserts bars, or updates them if that (ticker, trade_date) already exists.
     * Re-running an import is therefore always safe.
     */
    public int upsert(String ticker, List<YahooFinanceClient.DailyBar> bars, String source) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO daily_price (ticker, trade_date, open, high, low, close, adjusted_close, volume, source, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (ticker, trade_date) DO UPDATE SET
                    open = EXCLUDED.open,
                    high = EXCLUDED.high,
                    low = EXCLUDED.low,
                    close = EXCLUDED.close,
                    adjusted_close = EXCLUDED.adjusted_close,
                    volume = EXCLUDED.volume,
                    source = EXCLUDED.source,
                    fetched_at = EXCLUDED.fetched_at
                """, bars, 500, (ps, bar) -> {
            ps.setString(1, ticker);
            ps.setDate(2, Date.valueOf(bar.date()));
            ps.setBigDecimal(3, bar.open());
            ps.setBigDecimal(4, bar.high());
            ps.setBigDecimal(5, bar.low());
            ps.setBigDecimal(6, bar.close());
            ps.setBigDecimal(7, bar.adjustedClose());
            if (bar.volume() == null) ps.setNull(8, Types.BIGINT); else ps.setLong(8, bar.volume());
            ps.setString(9, source);
        });
        return bars.size();
    }

    public LocalDate latestIndexDate(String code) {
        return jdbc.sql("SELECT max(trade_date) FROM index_daily WHERE code = ?")
                .param(code)
                .query(LocalDate.class)
                .optional()
                .orElse(null);
    }

    public int upsertIndex(String code, List<YahooFinanceClient.DailyBar> bars, String source) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO index_daily (code, trade_date, open, high, low, close, volume, source, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (code, trade_date) DO UPDATE SET
                    open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low, close = EXCLUDED.close,
                    volume = EXCLUDED.volume, source = EXCLUDED.source, fetched_at = EXCLUDED.fetched_at
                """, bars, 500, (ps, bar) -> {
            ps.setString(1, code);
            ps.setDate(2, Date.valueOf(bar.date()));
            ps.setBigDecimal(3, bar.open());
            ps.setBigDecimal(4, bar.high());
            ps.setBigDecimal(5, bar.low());
            ps.setBigDecimal(6, bar.close());
            if (bar.volume() == null) ps.setNull(7, Types.BIGINT); else ps.setLong(7, bar.volume());
            ps.setString(8, source);
        });
        return bars.size();
    }

    public long startRun(String source, Instant startedAt) {
        return jdbc.sql("INSERT INTO import_run (source, started_at) VALUES (?, ?) RETURNING id")
                .params(source, Timestamp.from(startedAt))
                .query(Long.class)
                .single();
    }

    public void finishRun(long id, Instant finishedAt, int ok, int failed, int rows, String message) {
        jdbc.sql("""
                UPDATE import_run
                SET finished_at = ?, stocks_ok = ?, stocks_failed = ?, rows_written = ?, message = ?
                WHERE id = ?
                """)
                .params(Timestamp.from(finishedAt), ok, failed, rows, message, id)
                .update();
    }
}
