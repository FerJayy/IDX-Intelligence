package com.idxintelligence.backend.ingest;

import com.idxintelligence.backend.EmbeddedPostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class ScreenerAndTrackingTests extends EmbeddedPostgresTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 27, 19, 15, 0, 0, YahooFinanceClient.JAKARTA);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(), NOW.getZone());
    private static final List<String> SMALL_CAPS = List.of("AAAA", "BBBB", "CCCC", "DDDD", "EEEE", "FFFF", "GGGG");

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM screener_pick").update();
        jdbc.sql("DELETE FROM stock WHERE ticker IN ('AAAA','BBBB','CCCC','DDDD','EEEE','FFFF','GGGG')").update();
        for (String t : SMALL_CAPS) {
            jdbc.sql("INSERT INTO stock (ticker, name) VALUES (?, ?)").params(t, "Small " + t).update();
        }
    }

    private BrokerTrackingService tracking(int maxTotal) {
        return new BrokerTrackingService(jdbc, CLOCK, maxTotal, 5, 3);
    }

    @Test
    void userTrackingIsCappedPerDayAndGetsAShortHistoryWindow() {
        BrokerTrackingService service = tracking(80);

        assertThat(service.track("aaaa", BrokerTrackingService.Reason.USER)).isEqualTo(BrokerTrackingService.Outcome.TRACKED);
        assertThat(service.track("AAAA", BrokerTrackingService.Reason.USER)).isEqualTo(BrokerTrackingService.Outcome.ALREADY_TRACKED);
        assertThat(service.track("BBBB", BrokerTrackingService.Reason.USER)).isEqualTo(BrokerTrackingService.Outcome.TRACKED);
        assertThat(service.track("CCCC", BrokerTrackingService.Reason.USER)).isEqualTo(BrokerTrackingService.Outcome.TRACKED);
        assertThat(service.track("DDDD", BrokerTrackingService.Reason.USER)).isEqualTo(BrokerTrackingService.Outcome.DAILY_LIMIT_REACHED);
        assertThat(service.track("ZZZZ", BrokerTrackingService.Reason.USER)).isEqualTo(BrokerTrackingService.Outcome.UNKNOWN_STOCK);

        LocalDate backfillFrom = jdbc.sql("SELECT broker_backfill_from FROM stock WHERE ticker = 'AAAA'")
                .query(LocalDate.class).single();
        assertThat(backfillFrom).isEqualTo(NOW.toLocalDate().minusDays(BrokerTrackingService.NEW_STOCK_HISTORY_DAYS));
    }

    @Test
    void totalCapStopsNewTracking() {
        int alreadyTracked = jdbc.sql("SELECT count(*) FROM stock WHERE broker_tracked").query(Integer.class).single();

        assertThat(tracking(alreadyTracked).track("EEEE", BrokerTrackingService.Reason.SCREENER))
                .isEqualTo(BrokerTrackingService.Outcome.TOTAL_LIMIT_REACHED);
    }

    @Test
    void screenerPicksAreSavedWithTheirDateAndTracked() {
        IdxEdgeClient client = mock(IdxEdgeClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.screener()).thenReturn(new IdxEdgeClient.Screener("📊 **SCREENER V5.2** — Senin, 26 Oktober 2026", List.of(
                new IdxEdgeClient.ScreenerRow("FFFF", "Small FFFF Tbk.", "🥷 SINYAL SENYAP", "🌍 🧬", "mulai panas",
                        new BigDecimal("58.2"), new BigDecimal("11"), new BigDecimal("-5")),
                new IdxEdgeClient.ScreenerRow("GGGG", "Small GGGG Tbk.", "⏰ SINYAL TELAT", null, null, null, null, null))));

        ScreenerService.Result result = new ScreenerService(client, tracking(80), jdbc, jdbcTemplate).importLatest();

        assertThat(result.pickDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(result.picks()).isEqualTo(2);
        assertThat(result.newlyTracked()).isEqualTo(2);
        BigDecimal winRate = jdbc.sql("SELECT win_rate FROM screener_pick WHERE ticker = 'FFFF'").query(BigDecimal.class).single();
        assertThat(winRate).isEqualByComparingTo("58.2");
        String reason = jdbc.sql("SELECT broker_tracked_reason FROM stock WHERE ticker = 'GGGG'").query(String.class).single();
        assertThat(reason).isEqualTo("SCREENER");
    }

    @Test
    void parsesIndonesianDatesAndToleratesOtherHeadings() {
        assertThat(ScreenerService.parseDate("Jumat, 3 Januari 2027")).isEqualTo(LocalDate.of(2027, 1, 3));
        assertThat(ScreenerService.parseDate("SCREENER V6 (no date)")).isNull();
    }
}
