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
    private final CandleService candles;

    public StockController(MarketService marketService, CandleService candles) {
        this.marketService = marketService;
        this.candles = candles;
    }

    /** Chart candles: interval 15m|1h|4h|1d|1w|1mo; range must suit the interval, else its default is used. */
    @GetMapping("/{ticker}/candles")
    public CandleSeries candles(@PathVariable String ticker,
                                @RequestParam(defaultValue = "1d") String interval,
                                @RequestParam(required = false) String range) {
        return candles.stock(ticker, ChartInterval.fromCode(interval), range);
    }

    /** All stocks, searchable by code or name, filterable by sector, sortable; default: biggest first. */
    @GetMapping
    public StockList list(@RequestParam(required = false) String search,
                          @RequestParam(required = false) String sector,
                          @RequestParam(required = false) String change,
                          @RequestParam(required = false) String hsc,
                          @RequestParam(defaultValue = "marketCap") String sort,
                          @RequestParam(defaultValue = "desc") String order,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "50") int size) {
        return marketService.list(search, sector, change, hsc, sort, "asc".equalsIgnoreCase(order), page, size);
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
