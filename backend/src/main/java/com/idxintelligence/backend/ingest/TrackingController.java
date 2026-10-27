package com.idxintelligence.backend.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Load broker data" for a stock that isn't tracked yet. Open to visitors, so it is capped by
 * BrokerTrackingService (per day and in total); the first month of history is fetched in the
 * background right away, so the data shows up within about a minute.
 */
@RestController
public class TrackingController {

    private static final Logger log = LoggerFactory.getLogger(TrackingController.class);
    /** Requests for the first fetch: ~20 trading days x (all investors + foreign) plus a margin. */
    static final int FIRST_FETCH_REQUESTS = 60;

    private final BrokerTrackingService tracking;
    private final BrokerImportService brokerImport;
    private final TaskExecutor executor;

    // Spring Boot's general-purpose pool; the scheduler's own executor is also a TaskExecutor, hence the name.
    public TrackingController(BrokerTrackingService tracking, BrokerImportService brokerImport,
                              @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.tracking = tracking;
        this.brokerImport = brokerImport;
        this.executor = executor;
    }

    public record TrackResult(BrokerTrackingService.Outcome outcome) {
    }

    @PostMapping("/api/stocks/{ticker}/track")
    public TrackResult track(@PathVariable String ticker) {
        BrokerTrackingService.Outcome outcome = tracking.track(ticker, BrokerTrackingService.Reason.USER);
        if (outcome == BrokerTrackingService.Outcome.TRACKED) {
            executor.execute(() -> log.info("First broker fetch for {}: {}", ticker,
                    brokerImport.importStock(ticker, FIRST_FETCH_REQUESTS)));
        }
        return new TrackResult(outcome);
    }
}
