package com.idxintelligence.backend.broker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Real broker summaries from the database (imported from IDX Edge PRO by BrokerImportService).
 * Active unless {@code app.data-source=demo}.
 */
@Component
@ConditionalOnProperty(name = "app.data-source", havingValue = "database", matchIfMissing = true)
public class DatabaseBrokerDataProvider implements BrokerDataProvider {

    private final JdbcClient jdbc;

    public DatabaseBrokerDataProvider(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String sourceName() {
        return "IDX Edge PRO";
    }

    @Override
    public boolean isDemo() {
        return false;
    }

    @Override
    public List<BrokerDailyActivity> dailyActivity(String ticker, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT a.trade_date, a.broker_code, b.name, a.buy_volume, a.buy_value, a.sell_volume, a.sell_value,
                       a.foreign_buy_value, a.foreign_sell_value
                FROM broker_daily_activity a
                JOIN broker b ON b.code = a.broker_code
                WHERE a.ticker = ? AND a.trade_date BETWEEN ? AND ?
                """)
                .params(ticker.toUpperCase(), Date.valueOf(from), Date.valueOf(to))
                .query((rs, n) -> new BrokerDailyActivity(
                        rs.getDate("trade_date").toLocalDate(),
                        rs.getString("broker_code"),
                        rs.getString("name"),
                        rs.getLong("buy_volume"),
                        rs.getBigDecimal("buy_value"),
                        rs.getLong("sell_volume"),
                        rs.getBigDecimal("sell_value"),
                        rs.getBigDecimal("foreign_buy_value"),
                        rs.getBigDecimal("foreign_sell_value")))
                .list();
    }
}
