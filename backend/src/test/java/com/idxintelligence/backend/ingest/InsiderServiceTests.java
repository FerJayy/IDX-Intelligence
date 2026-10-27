package com.idxintelligence.backend.ingest;

import com.idxintelligence.backend.EmbeddedPostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
class InsiderServiceTests extends EmbeddedPostgresTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 28, 20, 0, 0, 0, YahooFinanceClient.JAKARTA);

    // Trimmed real response.
    private static final String RESPONSE = """
            {"stock_code":"BBRI","count":2,"total":56,"page":1,"page_size":10,"total_pages":6,"items":[
              {"name":"ALEXANDER DIPPO PARIS Y S","date":"2026-10-16","action_type":"buy","nationality":"local",
               "previous_value":"815,000","previous_percentage":"0.0005","current_value":"1,915,000",
               "current_percentage":"0.0013","changes_value":"+1,100,000","changes_percentage":"+0.0008",
               "price_formatted":"2,700","broker_code":"","badges":[]},
              {"name":"PT XYZ","date":"2026-10-10","action_type":"sell","nationality":"foreign",
               "previous_value":"9,000,000","previous_percentage":"6.1","current_value":"8,000,000",
               "current_percentage":"5.4","changes_value":"-1,000,000","changes_percentage":"-0.7",
               "price_formatted":"-","broker_code":"","badges":[]}]}
            """;

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        private Instant now = NOW.toInstant();

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

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM insider_transaction").update();
        jdbc.sql("DELETE FROM insider_fetch").update();
    }

    @Test
    void clientParsesTheDisclosurePage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://edge.test/api/insiders/BBRI"))
                .andExpect(header("X-API-Key", "test-key"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        IdxEdgeClient.InsiderPage page = new IdxEdgeClient(builder, "http://edge.test", "test-key").insiders("BBRI");

        server.verify();
        assertThat(page.total()).isEqualTo(56);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().getFirst().sharesChange()).isEqualTo("+1,100,000");
    }

    @Test
    void storesParsedTransactionsAndRefetchesOnlyAfterADay() {
        IdxEdgeClient client = mock(IdxEdgeClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.insiders("BBRI")).thenReturn(page());
        MovableClock clock = new MovableClock();
        InsiderService service = new InsiderService(jdbc, client, clock, 60);

        InsiderService.Insiders first = service.insiders("bbri");
        assertThat(first.total()).isEqualTo(56);
        assertThat(first.fetchedAt()).isNotNull();
        assertThat(first.transactions()).hasSize(2);
        InsiderService.Transaction buy = first.transactions().getFirst();
        assertThat(buy.action()).isEqualTo("buy");
        assertThat(buy.shareChange()).isEqualTo(1_100_000L);
        assertThat(buy.sharesAfter()).isEqualTo(1_915_000L);
        assertThat(buy.percentAfter()).isEqualByComparingTo("0.0013");
        assertThat(buy.price()).isEqualByComparingTo("2700");
        InsiderService.Transaction sell = first.transactions().get(1);
        assertThat(sell.shareChange()).isEqualTo(-1_000_000L);
        assertThat(sell.price()).isNull();   // "-" = not given, not zero

        clock.advance(Duration.ofHours(5));
        service.insiders("BBRI");
        verify(client, times(1)).insiders("BBRI");

        // A second fetch of the same disclosures doesn't duplicate them.
        clock.advance(Duration.ofHours(20));
        assertThat(service.insiders("BBRI").transactions()).hasSize(2);
        verify(client, times(2)).insiders("BBRI");
    }

    @Test
    void stopsFetchingAtTheDailyBudget() {
        IdxEdgeClient client = mock(IdxEdgeClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.insiders(anyString())).thenReturn(page());
        InsiderService service = new InsiderService(jdbc, client, new MovableClock(), 1);

        service.insiders("BBRI");
        InsiderService.Insiders untouched = service.insiders("BBCA");

        verify(client, never()).insiders("BBCA");
        assertThat(untouched.fetchedAt()).isNull();
        assertThat(untouched.transactions()).isEmpty();
    }

    @Test
    void unknownStockIs404() {
        InsiderService service = new InsiderService(jdbc, mock(IdxEdgeClient.class), new MovableClock(), 60);
        assertThatThrownBy(() -> service.insiders("ZZZZ")).isInstanceOf(ResponseStatusException.class);
    }

    private static IdxEdgeClient.InsiderPage page() {
        return new IdxEdgeClient.InsiderPage("BBRI", 56, List.of(
                new IdxEdgeClient.InsiderItem("ALEXANDER DIPPO PARIS Y S", "2026-10-16", "buy", "local",
                        "815,000", "0.0005", "1,915,000", "0.0013", "+1,100,000", "2,700"),
                new IdxEdgeClient.InsiderItem("PT XYZ", "2026-10-10", "sell", "foreign",
                        "9,000,000", "6.1", "8,000,000", "5.4", "-1,000,000", "-")));
    }
}
