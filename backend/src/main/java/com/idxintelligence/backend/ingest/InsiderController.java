package com.idxintelligence.backend.ingest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InsiderController {

    private final InsiderService insiders;

    public InsiderController(InsiderService insiders) {
        this.insiders = insiders;
    }

    /** Latest insider disclosures; refreshed from the source at most once a day per stock. */
    @GetMapping("/api/stocks/{ticker}/insiders")
    public InsiderService.Insiders insiders(@PathVariable String ticker) {
        return insiders.insiders(ticker);
    }
}
