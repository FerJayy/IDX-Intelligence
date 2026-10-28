package com.idxintelligence.backend.signal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShortlistController {

    private final ShortlistService shortlist;

    public ShortlistController(ShortlistService shortlist) {
        this.shortlist = shortlist;
    }

    /** Tracked stocks ranked by how many of the six checks they pass, with the list's measured record. */
    @GetMapping("/api/shortlist")
    public ShortlistService.Shortlist shortlist() {
        return shortlist.shortlist();
    }
}
