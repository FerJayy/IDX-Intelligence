package com.idxintelligence.backend.ingest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
public class BrokerImportRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public BrokerImportRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** A broker summary still to fetch: one stock, one trading day, one flow. */
    public record PendingDay(String ticker, LocalDate tradeDate, IdxEdgeClient.Flow flow, Long volume) {
    }

    private static final String PENDING_FROM = """
            FROM daily_price p
            JOIN stock s ON s.ticker = p.ticker AND s.broker_tracked AND s.active
            CROSS JOIN (VALUES ('ALL'), ('F')) AS f(flow)
            WHERE p.trade_date >= ?
              AND (s.broker_backfill_from IS NULL OR p.trade_date >= s.broker_backfill_from)
              AND (CAST(? AS VARCHAR) IS NULL OR p.ticker = ?)
              AND NOT EXISTS (SELECT 1 FROM broker_import_day d
                              WHERE d.ticker = p.ticker AND d.trade_date = p.trade_date AND d.flow = f.flow)
            """;

    /**
     * Trading days (taken from daily_price) whose broker summary is not stored yet. Newest first,
     * and for each day "ALL" before "F", because foreign figures are added onto the ALL rows.
     */
    public List<PendingDay> pending(LocalDate from, int limit) {
        return pending(from, null, limit);
    }

    /** @param ticker only this stock, or null for every tracked stock */
    public List<PendingDay> pending(LocalDate from, String ticker, int limit) {
        return jdbc.sql("SELECT p.ticker, p.trade_date, f.flow, p.volume " + PENDING_FROM
                        + " ORDER BY p.trade_date DESC, f.flow, p.ticker LIMIT ?")
                .params(Date.valueOf(from), ticker, ticker, limit)
                .query((rs, n) -> new PendingDay(
                        rs.getString("ticker"),
                        rs.getDate("trade_date").toLocalDate(),
                        IdxEdgeClient.Flow.valueOf(rs.getString("flow")),
                        rs.getObject("volume", Long.class)))
                .list();
    }

    public int countTracked() {
        return jdbc.sql("SELECT count(*) FROM stock WHERE broker_tracked AND active").query(Integer.class).single();
    }

    public int countPending(LocalDate from) {
        return countPending(from, null);
    }

    public int countPending(LocalDate from, String ticker) {
        return jdbc.sql("SELECT count(*) " + PENDING_FROM).params(Date.valueOf(from), ticker, ticker)
                .query(Integer.class).single();
    }

    /** Stores the all-investor summary for one stock-day, keeping any foreign figures already stored. */
    @Transactional
    public int saveAll(String ticker, LocalDate date, List<IdxEdgeClient.BrokerRow> rows, String source) {
        upsertBrokers(rows);
        jdbcTemplate.batchUpdate("""
                INSERT INTO broker_daily_activity
                    (ticker, trade_date, broker_code, buy_volume, buy_value, buy_frequency,
                     sell_volume, sell_value, sell_frequency, source, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (ticker, trade_date, broker_code) DO UPDATE SET
                    buy_volume = EXCLUDED.buy_volume, buy_value = EXCLUDED.buy_value,
                    buy_frequency = EXCLUDED.buy_frequency,
                    sell_volume = EXCLUDED.sell_volume, sell_value = EXCLUDED.sell_value,
                    sell_frequency = EXCLUDED.sell_frequency,
                    source = EXCLUDED.source, fetched_at = EXCLUDED.fetched_at
                """, rows, 200, (ps, row) -> {
            ps.setString(1, ticker);
            ps.setDate(2, Date.valueOf(date));
            ps.setString(3, row.brokerCode());
            ps.setLong(4, row.buyVolume());
            ps.setBigDecimal(5, row.buyValue());
            ps.setObject(6, row.buyFrequency());
            ps.setLong(7, row.sellVolume());
            ps.setBigDecimal(8, row.sellValue());
            ps.setObject(9, row.sellFrequency());
            ps.setString(10, source);
        });
        markFetched(ticker, date, IdxEdgeClient.Flow.ALL, rows.size());
        return rows.size();
    }

    /**
     * Adds foreign-investor figures to the stock-day's rows. Brokers absent from the foreign
     * summary get 0 (fetched, no foreign activity) rather than NULL (not fetched).
     */
    @Transactional
    public int saveForeign(String ticker, LocalDate date, List<IdxEdgeClient.BrokerRow> rows) {
        int baseRows = jdbc.sql("""
                UPDATE broker_daily_activity
                SET foreign_buy_volume = 0, foreign_buy_value = 0, foreign_sell_volume = 0, foreign_sell_value = 0
                WHERE ticker = ? AND trade_date = ?
                """).params(ticker, Date.valueOf(date)).update();
        if (baseRows == 0 && !rows.isEmpty()) {
            // The ALL summary for this day isn't stored yet (e.g. it failed); leave F pending to retry.
            return 0;
        }
        jdbcTemplate.batchUpdate("""
                UPDATE broker_daily_activity
                SET foreign_buy_volume = ?, foreign_buy_value = ?, foreign_sell_volume = ?, foreign_sell_value = ?
                WHERE ticker = ? AND trade_date = ? AND broker_code = ?
                """, rows, 200, (ps, row) -> {
            ps.setLong(1, row.buyVolume());
            ps.setBigDecimal(2, row.buyValue());
            ps.setLong(3, row.sellVolume());
            ps.setBigDecimal(4, row.sellValue());
            ps.setString(5, ticker);
            ps.setDate(6, Date.valueOf(date));
            ps.setString(7, row.brokerCode());
        });
        markFetched(ticker, date, IdxEdgeClient.Flow.F, rows.size());
        return rows.size();
    }

    private void upsertBrokers(List<IdxEdgeClient.BrokerRow> rows) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO broker (code, name) VALUES (?, ?)
                ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, updated_at = now()
                WHERE broker.name IS DISTINCT FROM EXCLUDED.name
                """, rows, 200, (ps, row) -> {
            ps.setString(1, row.brokerCode());
            ps.setString(2, row.brokerName() == null ? row.brokerCode() : row.brokerName());
        });
    }

    private void markFetched(String ticker, LocalDate date, IdxEdgeClient.Flow flow, int brokerCount) {
        jdbc.sql("""
                INSERT INTO broker_import_day (ticker, trade_date, flow, broker_count) VALUES (?, ?, ?, ?)
                ON CONFLICT (ticker, trade_date, flow) DO UPDATE SET broker_count = EXCLUDED.broker_count, fetched_at = now()
                """).params(ticker, Date.valueOf(date), flow.name(), brokerCount).update();
    }
}
