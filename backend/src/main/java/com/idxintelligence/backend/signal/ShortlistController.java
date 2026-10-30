package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.market.TodayPriceSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShortlistController {

    private final ShortlistService shortlist;
    private final ObjectProvider<TodayPriceSource> todayPrices;

    public ShortlistController(ShortlistService shortlist, ObjectProvider<TodayPriceSource> todayPrices) {
        this.shortlist = shortlist;
        this.todayPrices = todayPrices;
    }

    /**
     * Tracked stocks ranked by how many of the eight checks they pass, with the list's measured record.
     * During the session (until the evening import) the shown prices are today's; the checks stay end-of-day.
     */
    @GetMapping("/api/shortlist")
    public ShortlistService.Shortlist shortlist() {
        ShortlistService.Shortlist list = shortlist.shortlist();
        TodayPriceSource source = todayPrices.getIfAvailable();
        return source == null ? list : source.todayPrices().map(list::withTodayPrices).orElse(list);
    }
}
