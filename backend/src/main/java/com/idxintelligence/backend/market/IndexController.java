package com.idxintelligence.backend.market;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

/** Market indices. Only COMPOSITE (IHSG) for now. */
@RestController
@RequestMapping("/api/index")
public class IndexController {

    private final MarketDataProvider provider;
    private final CandleService candles;

    public IndexController(MarketDataProvider provider, CandleService candles) {
        this.provider = provider;
        this.candles = candles;
    }

    public record IndexDetail(boolean demo, String dataSource, String notice, OffsetDateTime generatedAt,
                              IndexSnapshot index) {
    }

    @GetMapping("/{code}")
    public IndexDetail index(@PathVariable String code) {
        if (!"COMPOSITE".equalsIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown index '" + code + "'");
        }
        return new IndexDetail(provider.isDemo(), provider.sourceName(),
                provider.isDemo() ? MarketService.DEMO_NOTICE : null, OffsetDateTime.now(MarketService.JAKARTA),
                provider.compositeIndex());
    }

    @GetMapping("/{code}/candles")
    public CandleSeries candles(@PathVariable String code,
                                @RequestParam(defaultValue = "1d") String interval,
                                @RequestParam(required = false) String range) {
        return candles.index(code, ChartInterval.fromCode(interval), range);
    }
}
