package com.idxintelligence.backend.ingest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LiveMoversController {

    private final LiveMoversService liveMovers;

    public LiveMoversController(LiveMoversService liveMovers) {
        this.liveMovers = liveMovers;
    }

    /** Today's top 10 lists while the market trades (until the evening import); available=false otherwise. */
    @GetMapping("/api/market/live-movers")
    public LiveMoversService.LiveMovers liveMovers() {
        return liveMovers.movers();
    }
}
