package com.idxintelligence.backend.broker;

import com.idxintelligence.backend.EmbeddedPostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BrokerProfileServiceTests extends EmbeddedPostgresTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 19);

    @Autowired
    private BrokerProfileService profiles;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM broker_daily_activity").update();
        jdbc.sql("DELETE FROM broker_import_day").update();
        jdbc.sql("DELETE FROM daily_price").update();
        jdbc.sql("INSERT INTO broker (code, name) VALUES ('RX', 'MACQUARIE SEKURITAS INDONESIA') ON CONFLICT (code) DO NOTHING").update();
        // RX buys UNTR at 1.000 on four days, but sells on the first: streak = 3 latest days.
        long[][] untr = {{0, 200}, {300, 0}, {300, 0}, {300, 0}};
        for (int i = 0; i < untr.length; i++) {
            activity("UNTR", MON.plusDays(i), untr[i][0], untr[i][1], 1000);
        }
        // RX sells BBCA at 5.000 on one day.
        activity("BBCA", MON.plusDays(3), 0, 400, 5000);
        price("UNTR", MON.plusDays(3), 1100);
        price("BBCA", MON.plusDays(3), 4500);
    }

    @Test
    void profileSplitsAccumulatingAndDistributingWithCostGapsAndStreaks() {
        BrokerProfileService.Profile profile = profiles.profile("rx", 20);

        assertThat(profile.name()).isEqualTo("MACQUARIE SEKURITAS INDONESIA");
        assertThat(profile.accumulating()).singleElement().satisfies(p -> {
            assertThat(p.ticker()).isEqualTo("UNTR");
            assertThat(p.netVolume()).isEqualTo(700);
            assertThat(p.averageBuyPrice()).isEqualByComparingTo("1000");
            assertThat(p.gap()).isEqualByComparingTo("0.1");   // 1.100 vs 1.000 average buy
            assertThat(p.buyStreak()).isEqualTo(3);
        });
        assertThat(profile.distributing()).singleElement().satisfies(p -> {
            assertThat(p.ticker()).isEqualTo("BBCA");
            assertThat(p.averageSellPrice()).isEqualByComparingTo("5000");
            assertThat(p.gap()).isEqualByComparingTo("-0.1");  // 4.500 vs 5.000 average sell
            assertThat(p.buyStreak()).isZero();
        });
    }

    @Test
    void brokersListShowsActiveBrokers() {
        assertThat(profiles.brokers()).anySatisfy(b -> {
            assertThat(b.code()).isEqualTo("RX");
            assertThat(b.stocks()).isEqualTo(2);
        });
    }

    @Test
    void unknownBrokerIs404() {
        assertThatThrownBy(() -> profiles.profile("QQ", 20)).isInstanceOf(ResponseStatusException.class);
    }

    private void activity(String ticker, LocalDate day, long buy, long sell, long price) {
        jdbc.sql("""
                INSERT INTO broker_daily_activity (ticker, trade_date, broker_code, buy_volume, buy_value, sell_volume, sell_value, source)
                VALUES (?, ?, 'RX', ?, ?, ?, ?, 'TEST')
                """).params(ticker, Date.valueOf(day), buy, buy * price, sell, sell * price).update();
        jdbc.sql("INSERT INTO broker_import_day (ticker, trade_date, flow, broker_count) VALUES (?, ?, 'ALL', 1) ON CONFLICT DO NOTHING")
                .params(ticker, Date.valueOf(day)).update();
    }

    private void price(String ticker, LocalDate day, long close) {
        jdbc.sql("INSERT INTO daily_price (ticker, trade_date, close, volume, source) VALUES (?, ?, ?, 1000, 'TEST')")
                .params(ticker, Date.valueOf(day), close).update();
    }
}
