package com.idxintelligence.backend.signal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SignalDataRepository {

    private final JdbcClient jdbc;

    public SignalDataRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record StockInfo(String ticker, String name, String sector, boolean brokerTracked) {
    }

    /** Latest trading day with any stored price, or null. */
    LocalDate latestDate() {
        return jdbc.sql("SELECT max(trade_date) FROM daily_price").query(LocalDate.class).optional().orElse(null);
    }

    Map<String, StockInfo> stocks() {
        Map<String, StockInfo> stocks = new LinkedHashMap<>();
        jdbc.sql("SELECT ticker, name, sector, broker_tracked FROM stock WHERE active ORDER BY ticker")
                .query((rs, n) -> new StockInfo(rs.getString("ticker"), rs.getString("name"), rs.getString("sector"),
                        rs.getBoolean("broker_tracked")))
                .list()
                .forEach(s -> stocks.put(s.ticker(), s));
        return stocks;
    }

    /**
     * Daily bars per ticker since {@code from}, oldest first. Prices are multiplied by
     * adjusted_close / close so splits and dividends don't show up as fake price jumps.
     *
     * @param trackedOnly only the broker-tracked stocks (a much smaller set) instead of all active ones
     */
    Map<String, List<Bar>> adjustedBars(LocalDate from, boolean trackedOnly) {
        Map<String, List<Bar>> bars = new HashMap<>();
        jdbc.sql("""
                SELECT p.ticker, p.trade_date, p.open, p.high, p.low, p.close, p.adjusted_close, p.volume
                FROM daily_price p
                JOIN stock s ON s.ticker = p.ticker AND s.active AND (s.broker_tracked OR NOT ?)
                WHERE p.trade_date >= ?
                ORDER BY p.ticker, p.trade_date
                """)
                .params(trackedOnly, Date.valueOf(from))
                .query((rs, n) -> {
                    double close = rs.getDouble("close");
                    BigDecimal adjusted = rs.getBigDecimal("adjusted_close");
                    double factor = adjusted == null || close == 0 ? 1 : adjusted.doubleValue() / close;
                    Long volume = rs.getObject("volume", Long.class);
                    bars.computeIfAbsent(rs.getString("ticker"), t -> new ArrayList<>()).add(new Bar(
                            rs.getDate("trade_date").toLocalDate(),
                            scaled(rs.getBigDecimal("open"), factor),
                            scaled(rs.getBigDecimal("high"), factor),
                            scaled(rs.getBigDecimal("low"), factor),
                            close * factor,
                            volume == null ? 0 : volume));
                    return null;
                })
                .list();
        return bars;
    }

    /** Same as {@link #adjustedBars(LocalDate, boolean)} for one stock, oldest first. */
    List<Bar> adjustedBars(String ticker, LocalDate from) {
        return jdbc.sql("""
                SELECT trade_date, open, high, low, close, adjusted_close, volume
                FROM daily_price WHERE ticker = ? AND trade_date >= ?
                ORDER BY trade_date
                """)
                .params(ticker, Date.valueOf(from))
                .query((rs, n) -> {
                    double close = rs.getDouble("close");
                    BigDecimal adjusted = rs.getBigDecimal("adjusted_close");
                    double factor = adjusted == null || close == 0 ? 1 : adjusted.doubleValue() / close;
                    Long volume = rs.getObject("volume", Long.class);
                    return new Bar(rs.getDate("trade_date").toLocalDate(), scaled(rs.getBigDecimal("open"), factor),
                            scaled(rs.getBigDecimal("high"), factor), scaled(rs.getBigDecimal("low"), factor),
                            close * factor, volume == null ? 0 : volume);
                })
                .list();
    }

    /** IHSG open and close by date, for comparing returns with the market. */
    Map<LocalDate, double[]> compositeIndex(LocalDate from) {
        Map<LocalDate, double[]> index = new HashMap<>();
        jdbc.sql("SELECT trade_date, open, close FROM index_daily WHERE code = 'COMPOSITE' AND trade_date >= ?")
                .param(Date.valueOf(from))
                .query((rs, n) -> {
                    BigDecimal open = rs.getBigDecimal("open");
                    double close = rs.getDouble("close");
                    index.put(rs.getDate("trade_date").toLocalDate(),
                            new double[]{open == null ? Double.NaN : open.doubleValue(), close});
                    return null;
                })
                .list();
        return index;
    }

    private static double scaled(BigDecimal value, double factor) {
        return value == null ? Double.NaN : value.doubleValue() * factor;
    }
}
