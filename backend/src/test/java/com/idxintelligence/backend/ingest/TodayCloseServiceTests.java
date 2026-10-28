package com.idxintelligence.backend.ingest;

import com.idxintelligence.backend.EmbeddedPostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class TodayCloseServiceTests extends EmbeddedPostgresTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final ZonedDateTime AFTER_CLOSE = TODAY.atTime(16, 30).atZone(YahooFinanceClient.JAKARTA);

    @Autowired
    private JdbcClient jdbc;

    private final YahooFinanceClient yahoo = mock(YahooFinanceClient.class);

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM daily_price WHERE ticker = 'BBCA' AND trade_date >= ?").param(Date.valueOf(TODAY.minusDays(1))).update();
        jdbc.sql("INSERT INTO daily_price (ticker, trade_date, close, volume, source) VALUES ('BBCA', ?, 4900, 1, 'TEST')")
                .param(Date.valueOf(TODAY.minusDays(1))).update();
        long nine = TODAY.atTime(9, 0).atZone(YahooFinanceClient.JAKARTA).toEpochSecond();
        when(yahoo.intradayBars(eq("BBCA.JK"), eq("15m"), any(Instant.class))).thenReturn(List.of(
                new YahooFinanceClient.IntradayBar(nine, new BigDecimal("4950"), new BigDecimal("5000"), new BigDecimal("4940"),
                        new BigDecimal("4990"), 100_000L),
                new YahooFinanceClient.IntradayBar(nine + 7 * 3600, new BigDecimal("5150"), new BigDecimal("5200"),
                        new BigDecimal("5150"), new BigDecimal("5175"), 200_000L)));
    }

    @Test
    void afterTheCloseGivesTodaysCloseAgainstTheLastStoredClose() {
        TodayCloseService service = new TodayCloseService(yahoo, jdbc, Clock.fixed(AFTER_CLOSE.toInstant(), YahooFinanceClient.JAKARTA));

        IdxEdgeClient.LiveQuote quote = service.todayClose("BBCA").orElseThrow();
        assertThat(quote.lastPrice()).isEqualByComparingTo("5175");
        assertThat(quote.change()).isEqualByComparingTo("275");
        assertThat(quote.changePercent()).isEqualByComparingTo("5.61");
        assertThat(quote.lot()).isEqualByComparingTo("3000");   // 300,000 shares

        service.todayClose("BBCA");
        verify(yahoo, times(1)).intradayBars(eq("BBCA.JK"), eq("15m"), any(Instant.class));   // cached
    }

    @Test
    void nothingDuringTheSessionOrOnceTodayIsStored() {
        TodayCloseService during = new TodayCloseService(yahoo, jdbc,
                Clock.fixed(AFTER_CLOSE.withHour(14).toInstant(), YahooFinanceClient.JAKARTA));
        assertThat(during.todayClose("BBCA")).isEmpty();

        jdbc.sql("INSERT INTO daily_price (ticker, trade_date, close, volume, source) VALUES ('BBCA', ?, 5175, 1, 'TEST')")
                .param(Date.valueOf(TODAY)).update();
        TodayCloseService after = new TodayCloseService(yahoo, jdbc, Clock.fixed(AFTER_CLOSE.toInstant(), YahooFinanceClient.JAKARTA));
        assertThat(after.todayClose("BBCA")).isEmpty();
        verify(yahoo, never()).intradayBars(any(), any(), any());
        jdbc.sql("DELETE FROM daily_price WHERE ticker = 'BBCA' AND trade_date = ?").param(Date.valueOf(TODAY)).update();
    }
}
