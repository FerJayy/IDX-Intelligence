package com.idxintelligence.backend.ingest;

import com.idxintelligence.backend.EmbeddedPostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static com.idxintelligence.backend.ingest.IdxEdgeClient.Flow.ALL;
import static com.idxintelligence.backend.ingest.IdxEdgeClient.Flow.F;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class BrokerImportServiceTests extends EmbeddedPostgresTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 26, 20, 0, 0, 0, YahooFinanceClient.JAKARTA);
    private static final LocalDate THU = LocalDate.of(2026, 10, 22);
    private static final LocalDate FRI = LocalDate.of(2026, 10, 23);

    @Autowired
    private BrokerImportRepository repository;

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private JdbcClient jdbc;

    private final IdxEdgeClient client = mock(IdxEdgeClient.class);

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM broker_daily_activity").update();
        jdbc.sql("DELETE FROM broker_import_day").update();
        jdbc.sql("DELETE FROM daily_price").update();
        jdbc.sql("DELETE FROM import_run").update();
        // Trading days come from daily_price: BBCA and TLKM traded Thursday and Friday (1,000 shares each).
        for (String ticker : List.of("BBCA", "TLKM")) {
            for (LocalDate day : List.of(THU, FRI)) {
                jdbc.sql("INSERT INTO daily_price (ticker, trade_date, close, volume, source) VALUES (?, ?, 100, 1000, 'TEST')")
                        .params(ticker, java.sql.Date.valueOf(day)).update();
            }
        }
        // A stock without broker tracking: its prices exist, but no broker summaries should be fetched.
        jdbc.sql("INSERT INTO stock (ticker, name, broker_tracked) VALUES ('UNTX', 'Untracked', false) ON CONFLICT DO NOTHING").update();
        jdbc.sql("INSERT INTO daily_price (ticker, trade_date, close, volume, source) VALUES ('UNTX', ?, 100, 1000, 'TEST')")
                .param(java.sql.Date.valueOf(FRI)).update();
        when(client.isConfigured()).thenReturn(true);
    }

    @Test
    void fetchesNewestDayFirstAndAllBeforeForeign() {
        when(client.brokerSummary(any(), any(), eq(ALL))).thenReturn(day(500, List.of(
                row("YP", 600, 400), row("AK", 400, 600))));
        when(client.brokerSummary(any(), any(), eq(F))).thenReturn(day(500, List.of(
                row("AK", 100, 300))));

        BrokerImportService.Result result = service(100).importBatch();

        assertThat(result.calls()).isEqualTo(8); // 2 tracked stocks x 2 days x 2 flows; UNTX skipped
        assertThat(result.stillPending()).isZero();
        InOrder order = inOrder(client);
        order.verify(client).brokerSummary("BBCA", FRI, ALL);
        order.verify(client).brokerSummary("TLKM", FRI, ALL);
        order.verify(client).brokerSummary("BBCA", FRI, F);
        order.verify(client).brokerSummary("TLKM", FRI, F);
        order.verify(client).brokerSummary("BBCA", THU, ALL);

        // AK has foreign figures; YP was absent from the foreign summary, so it gets 0, not NULL.
        assertThat(foreignBuy("BBCA", FRI, "AK")).isEqualTo(100L);
        assertThat(foreignBuy("BBCA", FRI, "YP")).isEqualTo(0L);
        assertThat(jdbc.sql("SELECT name FROM broker WHERE code = 'YP'").query(String.class).single()).isEqualTo("Broker YP");
    }

    @Test
    void secondRunDoesNotRefetch() {
        when(client.brokerSummary(any(), any(), any())).thenReturn(day(500, List.of(row("YP", 1000, 1000))));
        service(100).importBatch();

        BrokerImportService.Result second = service(100).importBatch();

        assertThat(second.calls()).isZero();
        assertThat(second.note()).isEqualTo("nothing to fetch");
    }

    @Test
    void emptySummaryForATradedDayStaysPending() {
        when(client.brokerSummary(any(), eq(FRI), any())).thenReturn(day(500, List.of())); // not published yet
        when(client.brokerSummary(any(), eq(THU), any())).thenReturn(day(500, List.of(row("YP", 1000, 1000))));

        BrokerImportService.Result result = service(100).importBatch();

        // One probe for Friday, then the rest of Friday is skipped; Thursday is fully fetched.
        verify(client, times(1)).brokerSummary(any(), eq(FRI), any());
        assertThat(result.stillPending()).isEqualTo(4); // Friday: 2 stocks x 2 flows
    }

    @Test
    void stopsAtTheQuotaReserve() {
        when(client.brokerSummary(any(), any(), any())).thenReturn(day(150, List.of(row("YP", 1000, 1000))));

        BrokerImportService.Result result = service(100).importBatch();

        assertThat(result.calls()).isEqualTo(1);
        assertThat(result.note()).contains("reserve");
    }

    @Test
    void quotaExhaustedStopsTheRun() {
        when(client.brokerSummary(any(), any(), any())).thenThrow(new IdxEdgeClient.QuotaExhaustedException());

        BrokerImportService.Result result = service(100).importBatch();

        assertThat(result.calls()).isZero();
        assertThat(result.note()).contains("quota");
        verify(client, times(1)).brokerSummary(any(), any(), any());
    }

    @Test
    void skippedWithoutApiKey() {
        when(client.isConfigured()).thenReturn(false);

        BrokerImportService.Result result = service(100).importBatch();

        assertThat(result.skipped()).isTrue();
        verify(client, never()).brokerSummary(any(), any(), any());
    }

    private BrokerImportService service(int maxCalls) {
        return new BrokerImportService(client, repository, priceRepository,
                Clock.fixed(NOW.toInstant(), NOW.getZone()), maxCalls, 150, 365, 0);
    }

    private Long foreignBuy(String ticker, LocalDate date, String broker) {
        return jdbc.sql("SELECT foreign_buy_volume FROM broker_daily_activity WHERE ticker = ? AND trade_date = ? AND broker_code = ?")
                .params(ticker, java.sql.Date.valueOf(date), broker).query(Long.class).single();
    }

    private static IdxEdgeClient.BrokerDay day(int remaining, List<IdxEdgeClient.BrokerRow> rows) {
        return new IdxEdgeClient.BrokerDay(rows, remaining);
    }

    private static IdxEdgeClient.BrokerRow row(String code, long buy, long sell) {
        return new IdxEdgeClient.BrokerRow(code, "Broker " + code, buy, BigDecimal.valueOf(buy * 100), 1,
                sell, BigDecimal.valueOf(sell * 100), 1);
    }
}
