package com.idxintelligence.backend.ingest;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LivePriceServiceTests {

    private static final ZonedDateTime TUESDAY_1430 = ZonedDateTime.of(2026, 10, 27, 14, 30, 0, 0, YahooFinanceClient.JAKARTA);

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant start) {
            now = start;
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

    private final IdxEdgeClient client = mock(IdxEdgeClient.class);

    private IdxEdgeClient.LiveQuote quote() {
        return new IdxEdgeClient.LiveQuote("BBCA", BigDecimal.valueOf(4890), BigDecimal.valueOf(-50), new BigDecimal("-1.02"),
                BigDecimal.valueOf(3_118_090), BigDecimal.valueOf(1_529_921_618_590L), 79_290L, new BigDecimal("4906.6"),
                "14:30:00", "open", "Sesi 2 berlangsung (13:30-15:50 WIB)", false);
    }

    @Test
    void oneRequestPerMinutePerStockSharedByViewers() {
        when(client.isConfigured()).thenReturn(true);
        when(client.livePrice(anyString())).thenReturn(quote());
        MovableClock clock = new MovableClock(TUESDAY_1430.toInstant());
        LivePriceService service = new LivePriceService(client, clock, 150);

        service.quote("bbca");
        service.quote("BBCA");
        clock.advance(Duration.ofSeconds(59));
        LivePriceService.LiveResult cached = service.quote("BBCA");
        clock.advance(Duration.ofSeconds(2));
        LivePriceService.LiveResult refreshed = service.quote("BBCA");

        assertThat(cached.status()).isEqualTo(LivePriceService.Status.LIVE);
        assertThat(refreshed.budgetLeft()).isEqualTo(148);
        verify(client, times(2)).livePrice("BBCA");
    }

    @Test
    void noRequestsOutsideMarketHours() {
        when(client.isConfigured()).thenReturn(true);
        LivePriceService service = new LivePriceService(client,
                Clock.fixed(TUESDAY_1430.withHour(17).toInstant(), YahooFinanceClient.JAKARTA), 150);

        assertThat(service.quote("BBCA").status()).isEqualTo(LivePriceService.Status.MARKET_CLOSED);
        assertThat(LivePriceService.marketHours(TUESDAY_1430.withDayOfMonth(31))).isFalse(); // Saturday
        verify(client, never()).livePrice(anyString());
    }

    @Test
    void noNewRequestsDuringTheLunchBreak() {
        when(client.isConfigured()).thenReturn(true);
        when(client.livePrice(anyString())).thenReturn(quote());
        MovableClock clock = new MovableClock(TUESDAY_1430.withHour(11).withMinute(58).toInstant());
        LivePriceService service = new LivePriceService(client, clock, 150);

        service.quote("BBCA");                       // 11:58, session 1
        clock.advance(Duration.ofMinutes(5));         // 12:03, break
        assertThat(service.quote("BBCA").status()).isEqualTo(LivePriceService.Status.LIVE);
        clock.advance(Duration.ofMinutes(60));        // 13:03, still break
        service.quote("BBCA");
        verify(client, times(1)).livePrice("BBCA");
        clock.advance(Duration.ofMinutes(30));        // 13:33, session 2
        service.quote("BBCA");
        verify(client, times(2)).livePrice("BBCA");
    }

    @Test
    void aHolidayStopsRequestsForTheRestOfTheDay() {
        when(client.isConfigured()).thenReturn(true);
        IdxEdgeClient.LiveQuote closed = new IdxEdgeClient.LiveQuote("BBCA", BigDecimal.valueOf(4890), BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, null, null, "16:00:00", "closed", "Pasar tutup", true);
        when(client.livePrice(anyString())).thenReturn(closed);
        MovableClock clock = new MovableClock(TUESDAY_1430.toInstant());
        LivePriceService service = new LivePriceService(client, clock, 150);

        assertThat(service.quote("BBCA").status()).isEqualTo(LivePriceService.Status.MARKET_CLOSED);
        clock.advance(Duration.ofMinutes(10));
        assertThat(service.quote("TLKM").status()).isEqualTo(LivePriceService.Status.MARKET_CLOSED);
        verify(client, times(1)).livePrice(anyString());
    }

    @Test
    void stopsAtTheDailyBudgetAndResetsTheNextDay() {
        when(client.isConfigured()).thenReturn(true);
        when(client.livePrice(anyString())).thenReturn(quote());
        MovableClock clock = new MovableClock(TUESDAY_1430.toInstant());
        LivePriceService service = new LivePriceService(client, clock, 2);

        service.quote("AAAA");
        service.quote("BBBB");
        assertThat(service.quote("CCCC").status()).isEqualTo(LivePriceService.Status.BUDGET_USED);

        clock.advance(Duration.ofDays(1));
        assertThat(service.quote("CCCC").status()).isEqualTo(LivePriceService.Status.LIVE);
    }
}
