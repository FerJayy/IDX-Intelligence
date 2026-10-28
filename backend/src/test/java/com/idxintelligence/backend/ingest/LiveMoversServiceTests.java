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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class LiveMoversServiceTests extends EmbeddedPostgresTest {

    private static final ZonedDateTime WEDNESDAY_1400 = ZonedDateTime.of(2026, 10, 28, 14, 0, 0, 0, YahooFinanceClient.JAKARTA);

    /** A clock the test can move. */
    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(ZonedDateTime start) {
            now = start.toInstant();
        }

        void set(ZonedDateTime time) {
            now = time.toInstant();
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return YahooFinanceClient.JAKARTA;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Autowired
    private JdbcClient jdbc;

    private final IdxEdgeClient client = mock(IdxEdgeClient.class);

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM daily_price WHERE trade_date >= ?").param(Date.valueOf(LocalDate.of(2026, 10, 27))).update();
        jdbc.sql("INSERT INTO daily_price (ticker, trade_date, close, volume, source) VALUES ('BBCA', ?, 4900, 1, 'TEST')")
                .param(Date.valueOf(LocalDate.of(2026, 10, 27))).update();
        when(client.isConfigured()).thenReturn(true);
        IdxEdgeClient.MarketState open = new IdxEdgeClient.MarketState("open", "Sesi 2");
        when(client.movers("chg")).thenReturn(new IdxEdgeClient.MoversPage(
                List.of(mover("BBCA", "2000000000000"), mover("ZZZZ-R", "5000")),
                List.of(mover("BBRI", "5000000")), null, open));
        when(client.movers("value")).thenReturn(new IdxEdgeClient.MoversPage(null, null, List.of(mover("BBCA", "2000000000000")), open));
    }

    private static IdxEdgeClient.Mover mover(String code, String value) {
        return new IdxEdgeClient.Mover(code, new BigDecimal("5000"), new BigDecimal("100"), new BigDecimal("2"),
                new BigDecimal("10"), new BigDecimal(value));
    }

    @Test
    void liveDuringTheSessionCachedTenMinutesAndTagsThinStocks() {
        MovableClock clock = new MovableClock(WEDNESDAY_1400);
        LiveMoversService service = new LiveMoversService(client, jdbc, clock, 80);

        LiveMoversService.LiveMovers movers = service.movers();
        assertThat(movers.available()).isTrue();
        assertThat(movers.gainers()).extracting(LiveMoversService.LiveMover::ticker).containsExactly("BBCA");  // unknown codes dropped
        assertThat(movers.gainers().getFirst().thin()).isFalse();
        assertThat(movers.losers().getFirst().thin()).isTrue();                                                // Rp5 juta traded
        assertThat(movers.mostActive()).hasSize(1);

        clock.advance(Duration.ofMinutes(5));
        service.movers();
        verify(client, times(1)).movers("chg");
        clock.advance(Duration.ofMinutes(6));
        service.movers();
        verify(client, times(2)).movers("chg");
    }

    @Test
    void offBeforeNineOnWeekendsAndOnceTodaysCloseIsImported() {
        MovableClock clock = new MovableClock(WEDNESDAY_1400.withHour(8));
        LiveMoversService service = new LiveMoversService(client, jdbc, clock, 80);
        assertThat(service.movers().available()).isFalse();

        clock.set(WEDNESDAY_1400.plusDays(3));   // Saturday
        assertThat(service.movers().available()).isFalse();

        clock.set(WEDNESDAY_1400.withHour(18));
        jdbc.sql("INSERT INTO daily_price (ticker, trade_date, close, volume, source) VALUES ('BBCA', ?, 5000, 1, 'TEST')")
                .param(Date.valueOf(LocalDate.of(2026, 10, 28))).update();
        assertThat(service.movers().available()).isFalse();
        verify(client, never()).movers(anyString());
    }

    @Test
    void stopsAtTheDailyBudget() {
        MovableClock clock = new MovableClock(WEDNESDAY_1400);
        LiveMoversService service = new LiveMoversService(client, jdbc, clock, 2);
        service.movers();
        clock.advance(Duration.ofMinutes(11));
        assertThat(service.movers().available()).isTrue();   // keeps showing the last list
        verify(client, times(1)).movers("chg");
    }
}
