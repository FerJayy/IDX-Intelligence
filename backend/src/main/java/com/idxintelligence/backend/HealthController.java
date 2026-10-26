package com.idxintelligence.backend;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final JdbcClient jdbc;

    public HealthController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "application", "IDX Intelligence",
            "message", "Backend is running",
            "database", databaseStatus()
        );
    }

    private String databaseStatus() {
        try {
            jdbc.sql("SELECT 1").query(Integer.class).single();
            return "UP";
        } catch (RuntimeException e) {
            return "DOWN";
        }
    }
}
