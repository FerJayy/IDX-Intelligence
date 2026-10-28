package com.idxintelligence.backend.signal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StockSummaryController {

    private final StockSummaryService summaries;

    public StockSummaryController(StockSummaryService summaries) {
        this.summaries = summaries;
    }

    /** Broker flow over 10 days / 1 month / 3 months side by side, plus a few plain-language facts. */
    @GetMapping("/api/stocks/{ticker}/summary")
    public StockSummaryService.Summary summary(@PathVariable String ticker) {
        return summaries.summary(ticker);
    }
}
