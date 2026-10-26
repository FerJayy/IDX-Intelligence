package com.idxintelligence.backend.market;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final MarketService marketService;

    public StockController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping("/{ticker}")
    public StockDetail stock(@PathVariable String ticker) {
        return marketService.stock(ticker);
    }

    @GetMapping("/{ticker}/prices")
    public PriceHistory prices(@PathVariable String ticker,
                               @RequestParam(defaultValue = "3M") String range) {
        return marketService.priceHistory(ticker, PriceRange.fromCode(range));
    }
}
