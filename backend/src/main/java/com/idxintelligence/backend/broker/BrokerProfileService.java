package com.idxintelligence.backend.broker;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * One broker's activity across the broker-tracked stocks: what it has been accumulating or
 * distributing, at what average prices, and how far today's price is from them.
 */
@Service
public class BrokerProfileService {

    public static final List<Integer> WINDOWS = List.of(10, 20, 60);
    private static final java.time.ZoneId JAKARTA = java.time.ZoneId.of("Asia/Jakarta");

    private final JdbcClient jdbc;

    public BrokerProfileService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A broker with its activity over the last ~20 broker days, for the brokers list. */
    public record BrokerSummaryRow(String code, String name, int stocks, BigDecimal grossValue, BigDecimal netValue) {
    }

    /**
     * @param gap        last price vs this broker's average buy price (net buyers) or sell price (net sellers), -1..
     * @param buyStreak  consecutive latest broker days this broker net bought the stock (0 if not buying now)
     */
    public record StockPosition(String ticker, String stockName, boolean hsc, int days, long buyVolume, BigDecimal buyValue,
                                BigDecimal averageBuyPrice, long sellVolume, BigDecimal sellValue,
                                BigDecimal averageSellPrice, long netVolume, BigDecimal netValue,
                                BigDecimal foreignNetValue, BigDecimal lastPrice, BigDecimal gap, int buyStreak) {
    }

    /** One day of this broker in one stock, as read from the database. */
    private record Row(String ticker, String stockName, boolean hsc, LocalDate date, long buyVolume, BigDecimal buyValue,
                       long sellVolume, BigDecimal sellValue, BigDecimal foreignBuy, BigDecimal foreignSell) {
    }

    public record Profile(OffsetDateTime generatedAt, String code, String name, int windowDays, LocalDate from,
                          LocalDate to, BigDecimal grossValue, BigDecimal netValue, List<StockPosition> accumulating,
                          List<StockPosition> distributing) {
    }

    public List<BrokerSummaryRow> brokers() {
        LocalDate latest = latestBrokerDay();
        if (latest == null) return List.of();
        return jdbc.sql("""
                SELECT a.broker_code, b.name, count(DISTINCT a.ticker) AS stocks,
                       sum(a.buy_value + a.sell_value) AS gross, sum(a.buy_value - a.sell_value) AS net
                FROM broker_daily_activity a JOIN broker b ON b.code = a.broker_code
                WHERE a.trade_date >= ?
                GROUP BY a.broker_code, b.name
                ORDER BY gross DESC
                """)
                .param(Date.valueOf(latest.minusDays(30)))
                .query((rs, n) -> new BrokerSummaryRow(rs.getString("broker_code"), rs.getString("name"),
                        rs.getInt("stocks"), rs.getBigDecimal("gross"), rs.getBigDecimal("net")))
                .list();
    }

    public Profile profile(String code, int windowDays) {
        String broker = code.toUpperCase();
        int window = WINDOWS.contains(windowDays) ? windowDays : 20;
        String name = jdbc.sql("SELECT name FROM broker WHERE code = ?").param(broker).query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown broker '" + code + "'"));
        LocalDate latest = latestBrokerDay();
        if (latest == null) {
            return new Profile(OffsetDateTime.now(JAKARTA), broker, name, window, null, null, BigDecimal.ZERO,
                    BigDecimal.ZERO, List.of(), List.of());
        }

        // The window = the last `window` days that have broker data at all (calendar days vary with holidays).
        List<LocalDate> days = jdbc.sql("""
                SELECT DISTINCT trade_date FROM broker_import_day WHERE flow = 'ALL' AND trade_date <= ?
                ORDER BY trade_date DESC LIMIT ?
                """)
                .params(Date.valueOf(latest), window)
                .query(LocalDate.class)
                .list();
        LocalDate from = days.isEmpty() ? latest : days.getLast();

        List<Row> rows = jdbc.sql("""
                SELECT a.ticker, s.name, (h.ticker IS NOT NULL) AS hsc, a.trade_date, a.buy_volume, a.buy_value,
                       a.sell_volume, a.sell_value, a.foreign_buy_value, a.foreign_sell_value
                FROM broker_daily_activity a
                JOIN stock s ON s.ticker = a.ticker
                LEFT JOIN hsc_stock h ON h.ticker = a.ticker
                WHERE a.broker_code = ? AND a.trade_date >= ?
                """)
                .params(broker, Date.valueOf(from))
                .query((rs, n) -> new Row(rs.getString("ticker"), rs.getString("name"), rs.getBoolean("hsc"),
                        rs.getDate("trade_date").toLocalDate(), rs.getLong("buy_volume"), rs.getBigDecimal("buy_value"),
                        rs.getLong("sell_volume"), rs.getBigDecimal("sell_value"),
                        rs.getBigDecimal("foreign_buy_value"), rs.getBigDecimal("foreign_sell_value")))
                .list();

        Map<String, List<Row>> byTicker = new HashMap<>();
        rows.forEach(r -> byTicker.computeIfAbsent(r.ticker(), t -> new ArrayList<>()).add(r));
        Map<String, BigDecimal> lastPrices = lastPrices(byTicker.keySet());
        Map<String, TreeSet<LocalDate>> brokerDays = tradingDaysWithBrokerData(byTicker.keySet(), from);

        List<StockPosition> positions = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        for (var entry : byTicker.entrySet()) {
            List<Row> stockRows = entry.getValue();
            long buyVolume = stockRows.stream().mapToLong(Row::buyVolume).sum();
            long sellVolume = stockRows.stream().mapToLong(Row::sellVolume).sum();
            BigDecimal buyValue = stockRows.stream().map(Row::buyValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sellValue = stockRows.stream().map(Row::sellValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal netValue = buyValue.subtract(sellValue);
            boolean foreignKnown = stockRows.stream().allMatch(r -> r.foreignBuy() != null && r.foreignSell() != null);
            BigDecimal foreignNet = foreignKnown
                    ? stockRows.stream().map(r -> r.foreignBuy().subtract(r.foreignSell())).reduce(BigDecimal.ZERO, BigDecimal::add)
                    : null;
            BigDecimal avgBuy = average(buyValue, buyVolume);
            BigDecimal avgSell = average(sellValue, sellVolume);
            BigDecimal last = lastPrices.get(entry.getKey());
            BigDecimal reference = netValue.signum() >= 0 ? avgBuy : avgSell;
            BigDecimal gap = last == null || reference == null ? null
                    : last.divide(reference, 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);

            positions.add(new StockPosition(entry.getKey(), stockRows.getFirst().stockName(), stockRows.getFirst().hsc(),
                    (int) stockRows.stream().map(Row::date).distinct().count(),
                    buyVolume, buyValue, avgBuy, sellVolume, sellValue, avgSell, buyVolume - sellVolume, netValue,
                    foreignNet, last, gap, buyStreak(stockRows, brokerDays.getOrDefault(entry.getKey(), new TreeSet<>()))));
            gross = gross.add(buyValue).add(sellValue);
            net = net.add(netValue);
        }

        List<StockPosition> accumulating = positions.stream().filter(p -> p.netValue().signum() > 0)
                .sorted(Comparator.comparing(StockPosition::netValue).reversed()).toList();
        List<StockPosition> distributing = positions.stream().filter(p -> p.netValue().signum() < 0)
                .sorted(Comparator.comparing(StockPosition::netValue)).toList();
        return new Profile(OffsetDateTime.now(JAKARTA), broker, name, window, from, latest, gross, net,
                accumulating, distributing);
    }

    /** Consecutive latest broker days (of that stock) on which this broker net bought it. */
    private static int buyStreak(List<Row> rowsOfStock, TreeSet<LocalDate> stockDays) {
        Map<LocalDate, Long> netByDay = new HashMap<>();
        rowsOfStock.forEach(r -> netByDay.merge(r.date(), r.buyVolume() - r.sellVolume(), Long::sum));
        int streak = 0;
        for (LocalDate day : stockDays.descendingSet()) {
            Long dayNet = netByDay.get(day);
            if (dayNet == null || dayNet <= 0) break;
            streak++;
        }
        return streak;
    }

    private Map<String, TreeSet<LocalDate>> tradingDaysWithBrokerData(java.util.Set<String> tickers, LocalDate from) {
        Map<String, TreeSet<LocalDate>> result = new HashMap<>();
        if (tickers.isEmpty()) return result;
        String placeholders = String.join(",", java.util.Collections.nCopies(tickers.size(), "?"));
        List<Object> params = new ArrayList<>(tickers);
        params.add(Date.valueOf(from));
        jdbc.sql("SELECT ticker, trade_date FROM broker_import_day WHERE flow = 'ALL' AND ticker IN (" + placeholders
                        + ") AND trade_date >= ?")
                .params(params)
                .query((rs, n) -> result.computeIfAbsent(rs.getString("ticker"), t -> new TreeSet<>())
                        .add(rs.getDate("trade_date").toLocalDate()))
                .list();
        return result;
    }

    private Map<String, BigDecimal> lastPrices(java.util.Set<String> tickers) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (tickers.isEmpty()) return result;
        String placeholders = String.join(",", java.util.Collections.nCopies(tickers.size(), "?"));
        jdbc.sql("""
                SELECT DISTINCT ON (ticker) ticker, close FROM daily_price
                WHERE ticker IN (%s) ORDER BY ticker, trade_date DESC
                """.formatted(placeholders))
                .params(new ArrayList<Object>(tickers))
                .query((rs, n) -> result.put(rs.getString("ticker"), rs.getBigDecimal("close")))
                .list();
        return result;
    }

    private LocalDate latestBrokerDay() {
        return jdbc.sql("SELECT max(trade_date) FROM broker_daily_activity").query(LocalDate.class).optional().orElse(null);
    }

    private static BigDecimal average(BigDecimal value, long volume) {
        return volume == 0 ? null : value.divide(BigDecimal.valueOf(volume), 2, RoundingMode.HALF_UP);
    }
}
