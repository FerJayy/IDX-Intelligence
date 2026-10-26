package com.idxintelligence.backend;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class AppConfig {

    /** Injected wherever "now" matters, so tests can fix the time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
