package com.idxintelligence.backend.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fills broker_daily_activity from IDX Edge PRO within the daily request quota. Works newest day
 * first, so each run first brings in the latest day(s) and then extends history backwards; repeated
 * daily runs gradually complete the backfill. Each (stock, day, flow) is fetched once.
 */
@Service
public class BrokerImportService {

    static final String SOURCE = "IDX_EDGE";
    private static final Logger log = LoggerFactory.getLogger(BrokerImportService.class);
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private final IdxEdgeClient client;
    private final BrokerImportRepository repository;
    private final PriceRepository runLog;
    private final Clock clock;
    private final int maxCallsPerRun;
    private final int quotaReserve;
    private final int backfillDays;
    private final long pauseMillis;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BrokerImportService(IdxEdgeClient client, BrokerImportRepository repository, PriceRepository runLog,
                               Clock clock,
                               @Value("${import.brokers.max-calls-per-run:800}") int maxCallsPerRun,
                               @Value("${import.brokers.quota-reserve:150}") int quotaReserve,
                               @Value("${import.brokers.backfill-days:365}") int backfillDays,
                               @Value("${import.brokers.pause-millis:300}") long pauseMillis) {
        this.client = client;
        this.repository = repository;
        this.runLog = runLog;
        this.clock = clock;
        this.maxCallsPerRun = maxCallsPerRun;
        this.quotaReserve = quotaReserve;
        this.backfillDays = backfillDays;
        this.pauseMillis = pauseMillis;
    }

    /** Daily at 19:30 WIB (after prices at 17:00), weekends too: the quota resets every day. */
    @Scheduled(cron = "${import.brokers.cron:0 30 19 * * *}", zone = "Asia/Jakarta")
    public void scheduledImport() {
        log.info("Scheduled broker import: {}", importBatch());
    }

    public Result importBatch() {
        if (!client.isConfigured()) {
            return Result.skipped("IDX_EDGE_API_KEY is not set");
        }
        if (!running.compareAndSet(false, true)) {
            return Result.skipped("an import is already running");
        }
        try {
            return runBatch();
        } finally {
            running.set(false);
        }
    }

    private Result runBatch() {
        Instant started = clock.instant();
        long runId = runLog.startRun(SOURCE, started);
        LocalDate from = started.atZone(YahooFinanceClient.JAKARTA).toLocalDate().minusDays(backfillDays);
        List<BrokerImportRepository.PendingDay> work = repository.pending(from, maxCallsPerRun);

        int calls = 0;
        int failed = 0;
        int consecutiveFailures = 0;
        int rows = 0;
        Integer remaining = null;
        String stopReason = work.isEmpty() ? "nothing to fetch" : "batch finished";
        LocalDate newest = null;
        LocalDate oldest = null;
        Set<LocalDate> notPublished = new HashSet<>();

        for (BrokerImportRepository.PendingDay day : work) {
            if (remaining != null && remaining <= quotaReserve) {
                stopReason = "kept " + remaining + " requests in reserve for today";
                break;
            }
            if (notPublished.contains(day.tradeDate())) continue;

            IdxEdgeClient.BrokerDay result;
            try {
                result = client.brokerSummary(day.ticker(), day.tradeDate(), day.flow());
                calls++;
                consecutiveFailures = 0;
            } catch (IdxEdgeClient.QuotaExhaustedException e) {
                stopReason = e.getMessage();
                break;
            } catch (RuntimeException e) {
                calls++;
                failed++;
                log.warn("Broker summary failed for {} {} {}: {}", day.ticker(), day.tradeDate(), day.flow(), e.getMessage());
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    stopReason = MAX_CONSECUTIVE_FAILURES + " failures in a row; last: " + e.getMessage();
                    break;
                }
                pause();
                continue;
            }
            remaining = result.remainingQuota();

            if (result.brokers().isEmpty()) {
                if (day.volume() != null && day.volume() > 0) {
                    // The stock traded, so an empty summary means the provider hasn't published this day yet.
                    // Leave it pending for a later run and skip the rest of that day.
                    notPublished.add(day.tradeDate());
                    log.info("Broker summary for {} not published yet; will retry later", day.tradeDate());
                    pause();
                    continue;
                }
            }

            if (day.flow() == IdxEdgeClient.Flow.ALL) {
                rows += repository.saveAll(day.ticker(), day.tradeDate(), result.brokers(), SOURCE);
                checkBalance(day, result.brokers());
            } else {
                rows += repository.saveForeign(day.ticker(), day.tradeDate(), result.brokers());
            }
            newest = newest == null || day.tradeDate().isAfter(newest) ? day.tradeDate() : newest;
            oldest = oldest == null || day.tradeDate().isBefore(oldest) ? day.tradeDate() : oldest;
            pause();
        }

        int stillPending = repository.countPending(from);
        String message = "%d calls, %d failed; %s; %d summaries still pending".formatted(calls, failed, stopReason, stillPending);
        runLog.finishRun(runId, clock.instant(), calls - failed, failed, rows, message);
        return new Result(false, stopReason, calls, failed, rows, remaining, stillPending, newest, oldest);
    }

    // Every share bought was sold by someone: total buy volume should equal the day's volume.
    private static void checkBalance(BrokerImportRepository.PendingDay day, List<IdxEdgeClient.BrokerRow> brokers) {
        long bought = brokers.stream().mapToLong(IdxEdgeClient.BrokerRow::buyVolume).sum();
        long sold = brokers.stream().mapToLong(IdxEdgeClient.BrokerRow::sellVolume).sum();
        if (bought != sold || (day.volume() != null && bought != day.volume())) {
            log.warn("Broker summary for {} {} does not balance: bought {}, sold {}, day volume {}",
                    day.ticker(), day.tradeDate(), bought, sold, day.volume());
        }
    }

    private void pause() {
        if (pauseMillis <= 0) return;
        try {
            Thread.sleep(pauseMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @param note           why the run stopped (or why it was skipped)
     * @param remainingQuota provider requests left today, if reported
     * @param stillPending   stock-day summaries not fetched yet within the backfill window
     */
    public record Result(boolean skipped, String note, int calls, int failed, int rowsWritten,
                         Integer remainingQuota, int stillPending, LocalDate newestDay, LocalDate oldestDay) {
        static Result skipped(String why) {
            return new Result(true, why, 0, 0, 0, null, 0, null, null);
        }
    }
}
