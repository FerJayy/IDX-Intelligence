package com.idxintelligence.backend.broker;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/brokers")
public class BrokerProfileController {

    private final BrokerProfileService profiles;

    public BrokerProfileController(BrokerProfileService profiles) {
        this.profiles = profiles;
    }

    /** Brokers active in the tracked stocks over the last month, most active first. */
    @GetMapping
    public List<BrokerProfileService.BrokerSummaryRow> brokers() {
        return profiles.brokers();
    }

    /** What one broker has been accumulating and distributing over the last 10, 20 or 60 broker days. */
    @GetMapping("/{code}")
    public BrokerProfileService.Profile profile(@PathVariable String code, @RequestParam(defaultValue = "20") int window) {
        return profiles.profile(code, window);
    }
}
