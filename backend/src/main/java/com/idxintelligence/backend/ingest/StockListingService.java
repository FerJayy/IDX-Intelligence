package com.idxintelligence.backend.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Keeps the {@code stock} table in line with the IDX listing (IDX Edge, ~20 requests for ~960
 * stocks). New stocks are added for price imports; existing names are kept; stocks missing from a
 * complete listing are marked inactive (not deleted, so their history stays).
 */
@Service
public class StockListingService {

    private static final Logger log = LoggerFactory.getLogger(StockListingService.class);
    // Regular IDX equity codes are four letters; this skips rights, warrants and test codes.
    private static final Pattern TICKER = Pattern.compile("^[A-Z]{4}$");
    private static final int MAX_PAGES = 40; // safety stop: 50 per page covers 2,000 listings

    private final IdxEdgeClient client;
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final PriceRepository runLog;
    private final Clock clock;

    public StockListingService(IdxEdgeClient client, JdbcClient jdbc, JdbcTemplate jdbcTemplate,
                               PriceRepository runLog, Clock clock) {
        this.client = client;
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.runLog = runLog;
        this.clock = clock;
    }

    /** Sundays 09:00 WIB: listings change rarely, so weekly is plenty. */
    @Scheduled(cron = "${import.stocks.cron:0 0 9 * * SUN}", zone = "Asia/Jakarta")
    public void scheduledSync() {
        log.info("Scheduled stock listing sync: {}", sync());
    }

    public Result sync() {
        if (!client.isConfigured()) {
            return new Result(true, "IDX_EDGE_API_KEY is not set", 0, 0, 0, 0);
        }
        // PostgreSQL keeps microseconds; a finer Java timestamp would make every row look older than
        // this sync and get it marked inactive. Milliseconds compare reliably.
        Instant started = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        long runId = runLog.startRun("IDX_EDGE_LISTING", started);

        List<IdxEdgeClient.ListedStock> listed = new ArrayList<>();
        int pages = 0;
        String problem = null;
        try {
            int totalPages = 1;
            for (int page = 1; page <= totalPages && page <= MAX_PAGES; page++) {
                IdxEdgeClient.ListingPage result = client.listingPage(page);
                totalPages = result.totalPages();
                listed.addAll(result.stocks());
                pages++;
            }
        } catch (RuntimeException e) {
            problem = e.getMessage();
            log.warn("Stock listing sync stopped after {} pages: {}", pages, problem);
        }

        List<IdxEdgeClient.ListedStock> stocks = listed.stream()
                .filter(s -> s.code() != null && TICKER.matcher(s.code().trim()).matches())
                .toList();
        Counts counts = save(stocks, started, problem == null);

        String message = "%d pages, %d stocks (%d new, %d marked inactive)%s".formatted(pages, stocks.size(),
                counts.added(), counts.deactivated(), problem == null ? "" : "; incomplete: " + problem);
        runLog.finishRun(runId, clock.instant(), problem == null ? 1 : 0, problem == null ? 0 : 1, stocks.size(), message);
        return new Result(false, message, pages, stocks.size(), counts.added(), counts.deactivated());
    }

    private record Counts(int added, int deactivated) {
    }

    // Not one transaction: a half-finished upsert is harmless (the next sync completes it), and
    // deactivation only runs after a complete listing.
    Counts save(List<IdxEdgeClient.ListedStock> stocks, Instant syncedAt, boolean complete) {
        int before = jdbc.sql("SELECT count(*) FROM stock").query(Integer.class).single();
        jdbcTemplate.batchUpdate("""
                INSERT INTO stock (ticker, name, listed_shares, market_cap, active, synced_at)
                VALUES (?, ?, ?, ?, true, ?)
                ON CONFLICT (ticker) DO UPDATE SET
                    listed_shares = EXCLUDED.listed_shares,
                    market_cap = EXCLUDED.market_cap,
                    active = true,
                    synced_at = EXCLUDED.synced_at
                """, stocks, 200, (ps, s) -> {
            ps.setString(1, s.code().trim());
            ps.setString(2, cleanName(s.name(), s.code()));
            ps.setObject(3, s.listedShares() == null ? null : s.listedShares().setScale(0, RoundingMode.HALF_UP).longValueExact());
            ps.setBigDecimal(4, s.marketCap() == null ? null : s.marketCap().setScale(2, RoundingMode.HALF_UP));
            ps.setTimestamp(5, Timestamp.from(syncedAt));
        });
        int added = jdbc.sql("SELECT count(*) FROM stock").query(Integer.class).single() - before;

        int deactivated = 0;
        // Only a complete listing can tell us a stock is gone; a partial one would wrongly deactivate.
        if (complete && !stocks.isEmpty()) {
            deactivated = jdbc.sql("UPDATE stock SET active = false WHERE active AND (synced_at IS NULL OR synced_at < ?)")
                    .param(Timestamp.from(syncedAt))
                    .update();
        }
        return new Counts(added, deactivated);
    }

    /** "Bank Central Asia Tbk." -> "Bank Central Asia": the suffix is on every listed company. */
    static String cleanName(String name, String code) {
        if (name == null || name.isBlank()) return code;
        return name.trim().replaceFirst("(?i)[\\s,]*Tbk\\.?$", "").trim();
    }

    /** @param skipped true when nothing was attempted (e.g. no API key) */
    public record Result(boolean skipped, String note, int pages, int stocks, int added, int deactivated) {
    }
}
