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

    /** All stocks, searchable by code or name, filterable by sector, sortable; default: biggest first. */
    @GetMapping
    public StockList list(@RequestParam(required = false) String search,
                          @RequestParam(required = false) String sector,
                          @RequestParam(defaultValue = "marketCap") String sort,
                          @RequestParam(defaultValue = "desc") String order,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "50") int size) {
        return marketService.list(search, sector, sort, "asc".equalsIgnoreCase(order), page, size);
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
