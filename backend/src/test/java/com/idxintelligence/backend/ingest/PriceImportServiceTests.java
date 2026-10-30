package com.idxintelligence.backend.ingest;

import com.idxintelligence.backend.EmbeddedPostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class PriceImportServiceTests extends EmbeddedPostgresTest {

    // Monday 2026-10-26, 14:30 WIB: market still open, so today's bar is not final.
    private static final ZonedDateTime DURING_TRADING =
            ZonedDateTime.of(2026, 10, 26, 14, 30, 0, 0, YahooFinanceClient.JAKARTA);
    private static final LocalDate TODAY = DURING_TRADING.toLocalDate();
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 23);

    @Autowired
    private PriceRepository repository;

    @Autowired
    private JdbcClient jdbc;

    private final YahooFinanceClient yahoo = mock(YahooFinanceClient.class);

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM daily_price").update();
        jdbc.sql("DELETE FROM import_run").update();
        jdbc.sql("UPDATE stock SET price_missing_since = NULL").update();
    }

    @Test
    void ifDueSkipsOnceTodaysImportSucceeded() {
        ZonedDateTime evening = DURING_TRADING.withHour(19).withMinute(40);
        ZonedDateTime afterFive = DURING_TRADING.withHour(17).withMinute(30);
        // A successful run that finished after 17:00 today: later scheduled runs have nothing to do.
        long run = repository.startRun("YAHOO", afterFive.minusMinutes(20).toInstant());
        repository.finishRun(run, afterFive.toInstant(), 900, 0, 900, null);

        PriceImportService.ImportResult result = service(evening).importAll(true);

        assertThat(result.alreadyDone()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(yahoo);
        // After 17:00, only a run from this evening counts: yesterday evening's run means today is still due.
        jdbc.sql("DELETE FROM import_run").update();
        long old = repository.startRun("YAHOO", afterFive.minusDays(1).toInstant());
        repository.finishRun(old, afterFive.minusDays(1).toInstant(), 900, 0, 900, null);
        assertThat(service(DURING_TRADING.withHour(23)).importAll(true).alreadyDone()).isFalse();
    }

    @Test
    void aRunWhereYahooRefusedMostStocksDoesNotCountAsDone() {
        ZonedDateTime evening = DURING_TRADING.withHour(23).withMinute(56);
        long run = repository.startRun("YAHOO", evening.minusMinutes(40).toInstant());
        repository.finishRun(run, evening.minusMinutes(2).toInstant(), 130, 800, 130, "HTTP 429 ...");

        assertThat(service(evening).importAll(true).alreadyDone()).isFalse();
    }

    @Test
    void stopsAfterManyFailuresInARow() {
        when(yahoo.dailyBars(anyString(), any(), any()))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.FORBIDDEN));

        PriceImportService.ImportResult result = service(DURING_TRADING).importAll();

        verify(yahoo, times(PriceImportService.MAX_FAILURES_IN_A_ROW)).dailyBars(anyString(), any(), any());
        assertThat(result.failures().getLast()).startsWith("stopped after");
    }

    @Test
    void ifDueStillCatchesUpAnIndexThatFailedTonight() {
        ZonedDateTime evening = DURING_TRADING.withHour(19).withMinute(40);
        jdbc.sql("DELETE FROM index_daily").update();
        // Stocks have Monday, but IHSG stopped at Friday (its request failed after the stocks).
        jdbc.sql("INSERT INTO daily_price (ticker, trade_date, close, volume, source) VALUES ('BBCA', ?, 100, 1000, 'TEST')")
                .param(java.sql.Date.valueOf(TODAY)).update();
        jdbc.sql("INSERT INTO index_daily (code, trade_date, close, source) VALUES ('COMPOSITE', ?, 5000, 'TEST')")
                .param(java.sql.Date.valueOf(FRIDAY)).update();
        long run = repository.startRun("YAHOO", evening.minusHours(2).toInstant());
        repository.finishRun(run, evening.minusHours(1).toInstant(), 900, 1, 900, "COMPOSITE: HTTP 429");
        when(yahoo.dailyBarsForSymbol(eq("^JKSE"), any(), any())).thenReturn(List.of(bar(TODAY, "5050", 1L)));

        PriceImportService.ImportResult result = service(evening).importAll(true);

        assertThat(result.alreadyDone()).isTrue();
        verify(yahoo, times(0)).dailyBars(anyString(), any(), any());   // the stocks aren't fetched again
        assertThat(repository.latestIndexDate("COMPOSITE")).isEqualTo(TODAY);
    }

    @Test
    void firstRunBackfillsAndSkipsUnfinishedAndPlaceholderBars() {
        when(yahoo.dailyBars(anyString(), any(), any())).thenReturn(List.of(
                bar(FRIDAY.minusDays(1), "5175", 286_928_500L),
                bar(FRIDAY, "5025", 0L),            // zero-volume placeholder: skipped
                bar(TODAY, "4980", 271_634_500L))); // still trading: skipped

        PriceImportService.ImportResult result = service(DURING_TRADING).importAll();

        int stocks = repository.trackedTickers().size();
        assertThat(result.stocksOk()).isEqualTo(stocks);
        assertThat(result.upToDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(result.rowsWritten()).isEqualTo(stocks); // one usable bar per stock
        verify(yahoo).dailyBars(eq("BBCA"), eq(TODAY.minusDays(1).minusYears(10)), eq(TODAY.minusDays(1)));
        assertThat(countRows("BBCA")).isEqualTo(1);
    }

    @Test
    void laterRunsOverlapRecentDaysAndUpdateInsteadOfDuplicating() {
        when(yahoo.dailyBars(anyString(), any(), any())).thenReturn(List.of(bar(FRIDAY, "5025", 100L)));
        service(DURING_TRADING).importAll();

        when(yahoo.dailyBars(anyString(), any(), any())).thenReturn(List.of(bar(FRIDAY, "5050", 200L)));
        service(DURING_TRADING).importAll();

        verify(yahoo).dailyBars(eq("BBCA"), eq(FRIDAY.minusDays(PriceImportService.OVERLAP_DAYS)), any());
        assertThat(countRows("BBCA")).isEqualTo(1);
        BigDecimal close = jdbc.sql("SELECT close FROM daily_price WHERE ticker = 'BBCA'").query(BigDecimal.class).single();
        assertThat(close).isEqualByComparingTo("5050");
    }

    @Test
    void oneFailingStockDoesNotStopTheOthersAndIsLogged() {
        when(yahoo.dailyBars(anyString(), any(), any())).thenReturn(List.of(bar(FRIDAY, "5025", 100L)));
        when(yahoo.dailyBars(eq("GOTO"), any(), any())).thenThrow(new IllegalStateException("HTTP 429"));

        PriceImportService.ImportResult result = service(DURING_TRADING).importAll();

        assertThat(result.stocksFailed()).isEqualTo(1);
        assertThat(result.failures()).containsExactly("GOTO: HTTP 429");
        assertThat(countRows("BBCA")).isEqualTo(1);
        String message = jdbc.sql("SELECT message FROM import_run").query(String.class).single();
        assertThat(message).contains("GOTO");
    }

    @Test
    void stocksYahooDoesNotKnowAreSkippedDailyAndRetriedWeekly() {
        when(yahoo.dailyBars(anyString(), any(), any())).thenReturn(List.of(bar(FRIDAY, "5025", 100L)));
        when(yahoo.dailyBars(eq("GOTO"), any(), any())).thenThrow(org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        PriceImportService.ImportResult first = service(DURING_TRADING).importAll();
        PriceImportService.ImportResult nextDay = service(DURING_TRADING.plusDays(1)).importAll();
        PriceImportService.ImportResult weekLater = service(DURING_TRADING.plusDays(7)).importAll();

        assertThat(first.stocksNotFound()).isEqualTo(1);
        assertThat(first.stocksFailed()).isZero();            // not counted as a failure
        assertThat(nextDay.stocksSkipped()).isEqualTo(1);     // GOTO not asked again the next day
        assertThat(weekLater.stocksSkipped()).isZero();       // retried a week later
        assertThat(weekLater.stocksNotFound()).isEqualTo(1);
        verify(yahoo, times(2)).dailyBars(eq("GOTO"), any(), any());
    }

    private PriceImportService service(ZonedDateTime now) {
        return new PriceImportService(yahoo, repository, Clock.fixed(now.toInstant(), now.getZone()), 10, 0);
    }

    private int countRows(String ticker) {
        return jdbc.sql("SELECT count(*) FROM daily_price WHERE ticker = ?").param(ticker).query(Integer.class).single();
    }

    private static YahooFinanceClient.DailyBar bar(LocalDate date, String close, Long volume) {
        BigDecimal price = new BigDecimal(close);
        return new YahooFinanceClient.DailyBar(date, price, price, price, price, price, volume);
    }
}
