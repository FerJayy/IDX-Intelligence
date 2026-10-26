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

    public WatchlistController(WatchlistService watchlist, BacktestService backtest) {
        this.watchlist = watchlist;
        this.backtest = backtest;
    }

    @GetMapping
    public WatchlistService.Watchlist watchlist() {
        return watchlist.watchlist();
    }

    @GetMapping("/backtest")
    public BacktestService.Result backtest(@RequestParam(defaultValue = "NEXT_OPEN") BacktestService.Entry entry) {
        return backtest.result(entry);
    }
}
