package com.idxintelligence.backend.ingest;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Replaces the HSC list when IDX publishes a new one (announcements only, no API). The whole list is
 * sent each time, so removals are handled too.
 */
@RestController
public class AdminHscController {

    private static final Pattern TICKER = Pattern.compile("^[A-Z]{4}$");

    private final AdminAuth adminAuth;
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public AdminHscController(AdminAuth adminAuth, JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.adminAuth = adminAuth;
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param asOf date of the IDX data; source = where the list comes from (e.g. the announcement number) */
    public record HscList(LocalDate asOf, String source, List<String> tickers) {
    }

    public record Result(int stocks, LocalDate asOf) {
    }

    @PutMapping("/api/admin/hsc")
    @Transactional
    public Result replace(@RequestHeader(value = "X-Admin-Token", required = false) String token, @RequestBody HscList list) {
        adminAuth.require(token);
        if (list.asOf() == null || list.source() == null || list.source().isBlank() || list.tickers() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "asOf, source and tickers are required");
        }
        List<String> tickers = list.tickers().stream().map(t -> t.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        List<String> invalid = tickers.stream().filter(t -> !TICKER.matcher(t).matches()).toList();
        if (!invalid.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not IDX codes: " + invalid);
        }
        jdbc.sql("DELETE FROM hsc_stock").update();
        jdbcTemplate.batchUpdate("INSERT INTO hsc_stock (ticker, as_of, source) VALUES (?, ?, ?)", tickers, 200, (ps, t) -> {
            ps.setString(1, t);
            ps.setDate(2, Date.valueOf(list.asOf()));
            ps.setString(3, list.source().trim());
        });
        return new Result(tickers.size(), list.asOf());
    }
}
