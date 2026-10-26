package com.idxintelligence.backend.ingest;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Row counts and storage per table, to keep an eye on Neon's 0.5 GB free-tier limit. */
@RestController
public class AdminStatsController {

    private final JdbcClient jdbc;
    private final AdminAuth adminAuth;

    public AdminStatsController(JdbcClient jdbc, AdminAuth adminAuth) {
        this.jdbc = jdbc;
        this.adminAuth = adminAuth;
    }

    @GetMapping("/api/admin/stats")
    public Stats stats(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminAuth.require(token);
        List<TableStats> tables = jdbc.sql("""
                SELECT relname AS name,
                       n_live_tup AS approximate_rows,
                       round(pg_total_relation_size(relid) / 1048576.0, 1) AS megabytes
                FROM pg_stat_user_tables
                ORDER BY pg_total_relation_size(relid) DESC
                """)
                .query((rs, n) -> new TableStats(rs.getString("name"), rs.getLong("approximate_rows"), rs.getBigDecimal("megabytes")))
                .list();
        BigDecimal databaseMegabytes = jdbc.sql("SELECT round(pg_database_size(current_database()) / 1048576.0, 1)")
                .query(BigDecimal.class).single();
        return new Stats(tables, databaseMegabytes);
    }

    public record TableStats(String name, long approximateRows, BigDecimal megabytes) {
    }

    public record Stats(List<TableStats> tables, BigDecimal databaseMegabytes) {
    }
}
