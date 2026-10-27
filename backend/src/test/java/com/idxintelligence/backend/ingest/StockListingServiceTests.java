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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class StockListingServiceTests extends EmbeddedPostgresTest {

    // Nanosecond precision on purpose: PostgreSQL keeps microseconds, which once made every row look stale.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T02:00:00.123456789Z"), ZoneOffset.UTC);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PriceRepository runLog;

    private final IdxEdgeClient client = mock(IdxEdgeClient.class);

    @BeforeEach
    void setUp() {
        when(client.isConfigured()).thenReturn(true);
        jdbc.sql("DELETE FROM stock WHERE ticker IN ('NEWS', 'GONE')").update();
        jdbc.sql("UPDATE stock SET active = true, synced_at = NULL").update();
    }

    @Test
    void addsNewStocksKeepsExistingNamesAndMarksMissingOnesInactive() {
        jdbc.sql("INSERT INTO stock (ticker, name) VALUES ('GONE', 'Delisted Co')").update();
        List<IdxEdgeClient.ListedStock> page1 = new java.util.ArrayList<>(seededStocks());
        page1.add(stock("NEWS", "Newly Listed Tbk."));
        page1.add(stock("BBCA-R", "Rights issue, not a regular share"));
        when(client.listingPage(1)).thenReturn(new IdxEdgeClient.ListingPage(page1.size(), 1, 1, page1));

        StockListingService.Result result = service().sync();

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.deactivated()).isEqualTo(1);
        assertThat(name("NEWS")).isEqualTo("Newly Listed");               // "Tbk." suffix dropped
        assertThat(name("BBCA")).isEqualTo("Bank Central Asia");          // existing name kept
        assertThat(active("BBCA")).isTrue();
        assertThat(active("GONE")).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM stock WHERE ticker = 'BBCA-R'").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT broker_tracked FROM stock WHERE ticker = 'NEWS'").query(Boolean.class).single()).isFalse();
    }

    @Test
    void anIncompleteListingNeverDeactivatesAnything() {
        List<IdxEdgeClient.ListedStock> page1 = List.of(stock("NEWS", "Newly Listed Tbk."));
        when(client.listingPage(1)).thenReturn(new IdxEdgeClient.ListingPage(100, 1, 2, page1));
        when(client.listingPage(2)).thenThrow(new IdxEdgeClient.QuotaExhaustedException());

        StockListingService.Result result = service().sync();

        assertThat(result.deactivated()).isZero();
        assertThat(result.note()).contains("incomplete");
        assertThat(active("BBCA")).isTrue();
        assertThat(name("NEWS")).isEqualTo("Newly Listed");
    }

    @Test
    void cleansCompanyNames() {
        assertThat(StockListingService.cleanName("Bank Rakyat Indonesia (Persero) Tbk.", "BBRI"))
                .isEqualTo("Bank Rakyat Indonesia (Persero)");
        assertThat(StockListingService.cleanName("  ", "XXXX")).isEqualTo("XXXX");
    }

    private StockListingService service() {
        return new StockListingService(client, jdbc, jdbcTemplate, runLog, CLOCK);
    }

    private List<IdxEdgeClient.ListedStock> seededStocks() {
        return jdbc.sql("SELECT ticker FROM stock WHERE ticker <> 'GONE'").query(String.class).list().stream()
                .map(t -> stock(t, t + " Tbk."))
                .toList();
    }

    private static IdxEdgeClient.ListedStock stock(String code, String name) {
        return new IdxEdgeClient.ListedStock(code, name, BigDecimal.valueOf(1_000_000_000L), BigDecimal.valueOf(5_000_000_000_000L));
    }

    private String name(String ticker) {
        return jdbc.sql("SELECT name FROM stock WHERE ticker = ?").param(ticker).query(String.class).single();
    }

    private boolean active(String ticker) {
        return jdbc.sql("SELECT active FROM stock WHERE ticker = ?").param(ticker).query(Boolean.class).single();
    }
}
