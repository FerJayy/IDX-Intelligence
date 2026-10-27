package com.idxintelligence.backend.market;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Real end-of-day prices from the database (imported from Yahoo Finance by PriceImportService).
 * Active unless {@code app.data-source=demo}.
 */
@Component
@ConditionalOnProperty(name = "app.data-source", havingValue = "database", matchIfMissing = true)
public class DatabaseMarketDataProvider implements MarketDataProvider {

    private static final String COMPOSITE = "COMPOSITE";

    private final JdbcClient jdbc;

    public DatabaseMarketDataProvider(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String sourceName() {
        return "Yahoo Finance";
    }

    @Override
    public boolean isDemo() {
        return false;
    }

    /** Latest IHSG value and the one before it, or null if the index hasn't been imported yet. */
    @Override
    public IndexSnapshot compositeIndex() {
        List<IndexSnapshot> rows = jdbc.sql("""
                SELECT trade_date, close, lag(close) OVER (ORDER BY trade_date) AS previous_close
                FROM (SELECT trade_date, close FROM index_daily WHERE code = ? ORDER BY trade_date DESC LIMIT 2) last_two
                ORDER BY trade_date DESC
                LIMIT 1
                """)
                .param(COMPOSITE)
                .query((rs, n) -> IndexSnapshot.of(COMPOSITE, "IDX Composite (IHSG)",
                        rs.getDate("trade_date").toLocalDate(),
                        rs.getBigDecimal("close"), rs.getBigDecimal("previous_close")))
                .list();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * Each tracked stock's latest bar and the close before it. A stock whose latest bar is older than
     * the market's latest trading day (e.g. suspended) keeps its last price but reports no daily change.
     */
    @Override
    public List<StockQuote> quotes() {
        return quotesWhere("s.active");
    }

    /** @param condition a fixed SQL condition from this class (never user input); values go in params */
    private List<StockQuote> quotesWhere(String condition, Object... params) {
        LocalDate marketDate = latestTradingDate();
        return jdbc.sql("""
                SELECT s.ticker, s.name, s.sector, s.market_cap, s.broker_tracked, (h.ticker IS NOT NULL) AS hsc,
                       cur.trade_date, cur.close, cur.volume, prev.close AS previous_close
                FROM stock s
                LEFT JOIN hsc_stock h ON h.ticker = s.ticker
                LEFT JOIN LATERAL (
                    SELECT trade_date, close, volume FROM daily_price p
                    WHERE p.ticker = s.ticker ORDER BY trade_date DESC LIMIT 1
                ) cur ON true
                LEFT JOIN LATERAL (
                    SELECT close FROM daily_price p
                    WHERE p.ticker = s.ticker AND p.trade_date < cur.trade_date ORDER BY trade_date DESC LIMIT 1
                ) prev ON true
                WHERE %s
                ORDER BY s.ticker
                """.formatted(condition))
                .params(params)
                .query((rs, n) -> {
                    Date date = rs.getDate("trade_date");
                    StockQuote quote = StockQuote.of(rs.getString("ticker"), rs.getString("name"), rs.getString("sector"),
                            date == null ? null : date.toLocalDate(),
                            rs.getBigDecimal("close"), rs.getBigDecimal("previous_close"),
                            rs.getObject("volume", Long.class),
                            rs.getBigDecimal("market_cap"), rs.getBoolean("broker_tracked"), rs.getBoolean("hsc"));
                    boolean stale = quote.tradeDate() == null || !quote.tradeDate().equals(marketDate);
                    return stale ? quote.withoutDailyChange() : quote;
                })
                .list();
    }

    /** One stock's quote, using the same rules as {@link #quotes()} but without loading every stock. */
    @Override
    public java.util.Optional<StockQuote> quote(String ticker) {
        return quotesWhere("s.ticker = ?", ticker.toUpperCase()).stream().findFirst();
    }

    @Override
    public List<PriceBar> dailyBars(String ticker, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT trade_date, open, high, low, close, volume, adjusted_close FROM daily_price
                WHERE ticker = ? AND trade_date BETWEEN ? AND ?
                ORDER BY trade_date
                """)
                .params(ticker.toUpperCase(), Date.valueOf(from), Date.valueOf(to))
                .query((rs, n) -> new PriceBar(rs.getDate("trade_date").toLocalDate(),
                        rs.getBigDecimal("open"), rs.getBigDecimal("high"), rs.getBigDecimal("low"),
                        rs.getBigDecimal("close"), rs.getObject("volume", Long.class),
                        rs.getBigDecimal("adjusted_close")))
                .list();
    }

    @Override
    public List<PriceBar> indexBars(String code, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT trade_date, open, high, low, close, volume FROM index_daily
                WHERE code = ? AND trade_date BETWEEN ? AND ?
                ORDER BY trade_date
                """)
                .params(code, Date.valueOf(from), Date.valueOf(to))
                .query((rs, n) -> new PriceBar(rs.getDate("trade_date").toLocalDate(),
                        rs.getBigDecimal("open"), rs.getBigDecimal("high"), rs.getBigDecimal("low"),
                        rs.getBigDecimal("close"), rs.getObject("volume", Long.class)))
                .list();
    }

    /** One query for all tickers instead of one per ticker. */
    @Override
    public java.util.Map<String, List<java.math.BigDecimal>> recentCloses(List<String> tickers, int days) {
        java.util.Map<String, List<java.math.BigDecimal>> result = new java.util.LinkedHashMap<>();
        if (tickers.isEmpty()) return result;
        tickers.forEach(t -> result.put(t, new java.util.ArrayList<>()));
        String placeholders = String.join(",", java.util.Collections.nCopies(tickers.size(), "?"));
        List<Object> params = new java.util.ArrayList<>(tickers);
        params.add(Date.valueOf(latestTradingDate().minusDays(days * 2L + 10)));
        params.add(days);
        String sql = "SELECT ticker, close FROM ("
                + " SELECT ticker, trade_date, close,"
                + " row_number() OVER (PARTITION BY ticker ORDER BY trade_date DESC) AS n"
                + " FROM daily_price WHERE ticker IN (" + placeholders + ") AND trade_date >= ?"
                + ") recent WHERE n <= ? ORDER BY ticker, trade_date";
        jdbc.sql(sql)
                .params(params)
                .query((rs, n) -> result.get(rs.getString("ticker")).add(rs.getBigDecimal("close")))
                .list();
        return result;
    }

    /** Most recent day with any stored price; today (Jakarta) if nothing is imported yet. */
    @Override
    public LocalDate latestTradingDate() {
        return jdbc.sql("SELECT max(trade_date) FROM daily_price")
                .query(LocalDate.class)
                .optional()
                .orElse(LocalDate.now(MarketService.JAKARTA));
    }
}
