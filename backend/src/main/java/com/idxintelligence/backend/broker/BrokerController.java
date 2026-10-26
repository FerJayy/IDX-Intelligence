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

    @GetMapping("/api/stocks/{ticker}/brokers")
    public BrokerSummary brokers(@PathVariable String ticker,
                                 @RequestParam(defaultValue = "3M") String range) {
        return brokerAnalysis.summary(ticker, PriceRange.fromCode(range));
    }
}
