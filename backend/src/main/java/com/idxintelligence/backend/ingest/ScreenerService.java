package com.idxintelligence.backend.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saves IDX Edge's daily market-wide screener picks (1 request) and starts broker tracking for
 * them, so our own broker analysis covers the stocks the screener finds interesting. The picks are
 * kept so their actual outcomes can be measured against the provider's claimed win rates.
 */
@Service
public class ScreenerService {

    static final String SOURCE = "IDX_EDGE_SCREENER";
    private static final Logger log = LoggerFactory.getLogger(ScreenerService.class);
    private static final Pattern INDONESIAN_DATE = Pattern.compile(
            "(\\d{1,2})\\s+(Januari|Februari|Maret|April|Mei|Juni|Juli|Agustus|September|Oktober|November|Desember)\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("januari", 1), Map.entry("februari", 2), Map.entry("maret", 3), Map.entry("april", 4),
            Map.entry("mei", 5), Map.entry("juni", 6), Map.entry("juli", 7), Map.entry("agustus", 8),
            Map.entry("september", 9), Map.entry("oktober", 10), Map.entry("november", 11), Map.entry("desember", 12));

    private final IdxEdgeClient client;
    private final BrokerTrackingService tracking;
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public ScreenerService(IdxEdgeClient client, BrokerTrackingService tracking, JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.client = client;
        this.tracking = tracking;
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 19:15 WIB, before the 19:30 broker import, so newly tracked picks get their data the same evening. */
    @Scheduled(cron = "${import.screener.cron:0 15 19 * * MON-FRI}", zone = "Asia/Jakarta")
    public void scheduledImport() {
        log.info("Scheduled screener import: {}", importLatest());
    }

    public Result importLatest() {
        if (!client.isConfigured()) {
            return new Result(true, "IDX_EDGE_API_KEY is not set", null, 0, 0);
        }
        IdxEdgeClient.Screener screener = client.screener();
        LocalDate pickDate = parseDate(screener.date());
        if (pickDate == null) {
            // The heading's format changed: fall back to our latest trading day.
            pickDate = jdbc.sql("SELECT max(trade_date) FROM daily_price").query(LocalDate.class).optional()
                    .orElse(LocalDate.now(YahooFinanceClient.JAKARTA));
        }
        List<IdxEdgeClient.ScreenerRow> rows = screener.rows() == null ? List.of()
                : screener.rows().stream().filter(r -> r.code() != null && !r.code().isBlank()).toList();

        LocalDate date = pickDate;
        jdbcTemplate.batchUpdate("""
                INSERT INTO screener_pick (pick_date, ticker, bucket, summary, note, win_rate, potential, drawdown, source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (pick_date, ticker) DO UPDATE SET
                    bucket = EXCLUDED.bucket, summary = EXCLUDED.summary, note = EXCLUDED.note,
                    win_rate = EXCLUDED.win_rate, potential = EXCLUDED.potential, drawdown = EXCLUDED.drawdown,
                    fetched_at = now()
                """, rows, 100, (ps, r) -> {
            ps.setDate(1, Date.valueOf(date));
            ps.setString(2, r.code().trim().toUpperCase(Locale.ROOT));
            ps.setString(3, r.bucket() == null ? "?" : r.bucket());
            ps.setString(4, r.summary());
            ps.setString(5, r.note());
            ps.setBigDecimal(6, r.winRate());
            ps.setBigDecimal(7, r.potential());
            ps.setBigDecimal(8, r.drawdown());
            ps.setString(9, SOURCE);
        });

        int newlyTracked = 0;
        for (IdxEdgeClient.ScreenerRow r : rows) {
            if (tracking.track(r.code().trim(), BrokerTrackingService.Reason.SCREENER) == BrokerTrackingService.Outcome.TRACKED) {
                newlyTracked++;
            }
        }
        return new Result(false, "%d picks for %s; %d newly tracked".formatted(rows.size(), date, newlyTracked),
                date, rows.size(), newlyTracked);
    }

    /** "Senin, 26 Oktober 2026" anywhere in the heading -> 2026-10-26; null if not found. */
    static LocalDate parseDate(String heading) {
        if (heading == null) return null;
        Matcher m = INDONESIAN_DATE.matcher(heading);
        if (!m.find()) return null;
        return LocalDate.of(Integer.parseInt(m.group(3)), MONTHS.get(m.group(2).toLowerCase(Locale.ROOT)),
                Integer.parseInt(m.group(1)));
    }

    public record Result(boolean skipped, String note, LocalDate pickDate, int picks, int newlyTracked) {
    }
}
