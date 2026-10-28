package com.idxintelligence.backend.broker;

import com.idxintelligence.backend.market.PriceRange;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BrokerController {

    private final BrokerAnalysisService brokerAnalysis;

    public BrokerController(BrokerAnalysisService brokerAnalysis) {
        this.brokerAnalysis = brokerAnalysis;
    }

    /** range: 1D (latest broker day), 1W (last 5 broker days), or a calendar range 1M, 3M, 6M, 1Y. */
    @GetMapping("/api/stocks/{ticker}/brokers")
    public BrokerSummary brokers(@PathVariable String ticker,
                                 @RequestParam(defaultValue = "3M") String range) {
        return switch (range.toUpperCase()) {
            case "1D" -> brokerAnalysis.recentSummary(ticker, 1, "1D");
            case "1W" -> brokerAnalysis.recentSummary(ticker, 5, "1W");
            default -> brokerAnalysis.summary(ticker, PriceRange.fromCode(range));
        };
    }
}
