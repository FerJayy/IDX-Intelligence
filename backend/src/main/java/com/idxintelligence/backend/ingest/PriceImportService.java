package com.idxintelligence.backend.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Imports daily prices for every stock in the {@code stock} table from Yahoo into
 * {@code daily_price}. First run backfills history; later runs fetch only recent days.
 */
@Service
public class PriceImportService {

    static final String SOURCE = "YAHOO";
    private static final Logger log = LoggerFactory.getLogger(PriceImportService.class);

    // IDX closes at 16:00 WIB (post-closing until ~16:15). Before this time today's bar is still moving.
    static final LocalTime DAY_FINAL_AFTER = LocalTime.of(17, 0);
    // Re-fetch this many recent days on every run to pick up late corrections.
    static final int OVERLAP_DAYS = 7;
    /** Our index code -> Yahoo symbol. */
    static final Map<String, String> INDICES = Map.of("COMPOSITE", "^JKSE");

    private final YahooFinanceClient yahoo;
    private final PriceRepository repository;
    private final Clock clock;
    private final int backfillYears;
    private final long pauseMillis;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PriceImportService(YahooFinanceClient yahoo, PriceRepository repository, Clock clock,
                              @Value("${import.prices.backfill-years:5}") int backfillYears,
                              @Value("${import.prices.pause-millis:250}") long pauseMillis) {
        this.yahoo = yahoo;
        this.repository = repository;
        this.clock = clock;
        this.backfillYears = backfillYears;
        this.pauseMillis = pauseMillis;
    }

    /** Weekdays at 17:00 WIB, while the backend is running. */
    @Scheduled(cron = "${import.prices.cron:0 0 17 * * MON-FRI}", zone = "Asia/Jakarta")
    public void scheduledImport() {
        ImportResult result = importAll();
        log.info("Scheduled price import: {}", result);
    }

    public ImportResult importAll() {
        if (!running.compareAndSet(false, true)) {
            return ImportResult.skippedBecauseRunning();
        }
        try {
            return runImport();
        } finally {
            running.set(false);
        }
    }

    private ImportResult runImport() {
        Instant started = clock.instant();
        long runId = repository.startRun(SOURCE, started);
        ZonedDateTime now = started.atZone(YahooFinanceClient.JAKARTA);
        LocalDate lastFinalDay = now.toLocalTime().isBefore(DAY_FINAL_AFTER)
                ? now.toLocalDate().minusDays(1)
                : now.toLocalDate();

        int ok = 0;
        int rows = 0;
        List<String> failures = new ArrayList<>();
        List<String> tickers = repository.trackedTickers();
        for (String ticker : tickers) {
            try {
                LocalDate latest = repository.latestTradeDate(ticker);
                LocalDate from = latest == null
                        ? lastFinalDay.minusYears(backfillYears)
                        : latest.minusDays(OVERLAP_DAYS);
                List<YahooFinanceClient.DailyBar> bars = withRetry(() -> yahoo.dailyBars(ticker, from, lastFinalDay)).stream()
                        .filter(bar -> !bar.date().isAfter(lastFinalDay))
                        // Yahoo sometimes emits zero-volume placeholder rows for holidays.
                        .filter(bar -> bar.volume() == null || bar.volume() > 0)
                        .toList();
                rows += repository.upsert(ticker, bars, SOURCE);
                ok++;
            } catch (RuntimeException e) {
                log.warn("Price import failed for {}: {}", ticker, e.getMessage());
                failures.add(ticker + ": " + e.getMessage());
            }
            pause();
        }

        // Market indices go to index_daily; failures are reported like stocks but not counted as one.
        for (var index : INDICES.entrySet()) {
            try {
                LocalDate latest = repository.latestIndexDate(index.getKey());
                LocalDate from = latest == null
                        ? lastFinalDay.minusYears(backfillYears)
                        : latest.minusDays(OVERLAP_DAYS);
                List<YahooFinanceClient.DailyBar> bars = yahoo.dailyBarsForSymbol(index.getValue(), from, lastFinalDay)
                        .stream()
                        .filter(bar -> !bar.date().isAfter(lastFinalDay))
                        .toList();
                rows += repository.upsertIndex(index.getKey(), bars, SOURCE);
            } catch (RuntimeException e) {
                log.warn("Index import failed for {}: {}", index.getKey(), e.getMessage());
                failures.add(index.getKey() + ": " + e.getMessage());
            }
        }

        String message = failures.isEmpty() ? null : String.join("\n", failures);
        repository.finishRun(runId, clock.instant(), ok, failures.size(), rows, message);
        return new ImportResult(false, tickers.size(), ok, failures.size(), rows, lastFinalDay, failures);
    }

    // Yahoo occasionally answers 429 when asked for many stocks in a row: wait and try again.
    private static final int RETRIES = 2;
    private static final long RETRY_WAIT_MILLIS = 20_000;

    private <T> T withRetry(java.util.function.Supplier<T> call) {
        for (int attempt = 0; ; attempt++) {
            try {
                return call.get();
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                if (attempt >= RETRIES || pauseMillis <= 0) throw e;
                log.info("Yahoo rate limit; waiting {} s before retrying", RETRY_WAIT_MILLIS * (attempt + 1) / 1000);
                sleep(RETRY_WAIT_MILLIS * (attempt + 1));
            }
        }
    }

    private void pause() {
        if (pauseMillis > 0) sleep(pauseMillis); // be gentle with Yahoo
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record ImportResult(boolean alreadyRunning, int stocks, int stocksOk, int stocksFailed,
                               int rowsWritten, LocalDate upToDate, List<String> failures) {
        static ImportResult skippedBecauseRunning() {
            return new ImportResult(true, 0, 0, 0, 0, null, List.of());
        }
    }
}
