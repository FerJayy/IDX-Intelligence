package com.idxintelligence.backend.signal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlist;
    private final BacktestService backtest;
    private final RadarService radar;

    public WatchlistController(WatchlistService watchlist, BacktestService backtest, RadarService radar) {
        this.watchlist = watchlist;
        this.backtest = backtest;
        this.radar = radar;
    }

    @GetMapping
    public WatchlistService.Watchlist watchlist() {
        return watchlist.watchlist();
    }

    /** Broker Radar over the last 5, 10 or 20 broker days (default 10). */
    @GetMapping("/radar")
    public RadarService.Radar radar(@RequestParam(defaultValue = "10") int window) {
        return radar.radar(window);
    }

    @GetMapping("/backtest")
    public BacktestService.Result backtest(@RequestParam(defaultValue = "NEXT_OPEN") BacktestService.Entry entry) {
        return backtest.result(entry);
    }
}
