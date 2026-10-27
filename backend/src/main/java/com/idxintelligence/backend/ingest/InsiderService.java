package com.idxintelligence.backend.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Insider transactions for the stock someone is looking at. Fetched from IDX Edge when a stock page
 * asks and the stored copy is more than a day old, from a small daily budget of its own, so browsing
 * never eats the broker import's quota. Disclosures are rare, so a day-old copy is fine.
 */
@Service
public class InsiderService {

    private static final Logger log = LoggerFactory.getLogger(InsiderService.class);
    static final Duration REFRESH_AFTER = Duration.ofHours(24);
    static final int SHOWN = 10;
    public static final String SOURCE = "IDX Edge PRO (IDX disclosures)";

    private final JdbcClient jdbc;
    private final IdxEdgeClient client;
    private final Clock clock;
    private final int dailyBudget;
    private LocalDate budgetDay;
    private int usedToday;

    public InsiderService(JdbcClient jdbc, IdxEdgeClient client, Clock clock,
                          @Value("${insiders.daily-budget:60}") int dailyBudget) {
        this.jdbc = jdbc;
        this.client = client;
        this.clock = clock;
        this.dailyBudget = dailyBudget;
    }

    /**
     * @param shareChange signed number of shares bought (+) or sold (-)
     * @param percentBefore percent of all shares (1.5 = 1,5%); null when not given
     */
    public record Transaction(LocalDate date, String name, String action, String nationality, Long sharesBefore,
                              Long sharesAfter, Long shareChange, BigDecimal percentBefore, BigDecimal percentAfter,
                              BigDecimal price) {
    }

    /**
     * @param fetchedAt when the source was last asked; null = never (no key, or budget used)
     * @param total     all disclosures the source knows for this stock; only the latest are shown
     */
    public record Insiders(String ticker, String source, OffsetDateTime fetchedAt, Integer total,
                           List<Transaction> transactions) {
    }

    public Insiders insiders(String ticker) {
        String code = ticker.toUpperCase();
        boolean known = jdbc.sql("SELECT count(*) FROM stock WHERE ticker = ?").param(code).query(Integer.class).single() > 0;
        if (!known) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown stock '" + ticker + "'");

        Instant lastFetch = lastFetch(code);
        if ((lastFetch == null || lastFetch.plus(REFRESH_AFTER).isBefore(clock.instant()))
                && client.isConfigured() && takeFromBudget()) {
            try {
                store(code, client.insiders(code));
            } catch (RuntimeException e) {
                // Show what is stored; the next page view after the refresh window tries again.
                log.warn("Insider fetch for {} failed: {}", code, e.getMessage());
            }
        }
        return read(code);
    }

    private void store(String code, IdxEdgeClient.InsiderPage page) {
        List<IdxEdgeClient.InsiderItem> items = page.items() == null ? List.of() : page.items();
        for (IdxEdgeClient.InsiderItem item : items) {
            LocalDate date = date(item.date());
            if (date == null || item.name() == null || item.name().isBlank()) continue;
            jdbc.sql("""
                    INSERT INTO insider_transaction (ticker, trade_date, insider_name, action, nationality, shares_before,
                        shares_after, shares_change, percent_before, percent_after, price, source)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT ON CONSTRAINT insider_transaction_identity DO UPDATE SET
                        nationality = EXCLUDED.nationality, shares_before = EXCLUDED.shares_before,
                        shares_change = EXCLUDED.shares_change, percent_before = EXCLUDED.percent_before,
                        percent_after = EXCLUDED.percent_after, price = EXCLUDED.price, fetched_at = now()
                    """)
                    .params(code, date, item.name().trim(), action(item.action()), blankToNull(item.nationality()),
                            whole(item.sharesBefore()), whole(item.sharesAfter()), whole(item.sharesChange()),
                            decimal(item.percentBefore()), decimal(item.percentAfter()), decimal(item.price()), "IDX_EDGE")
                    .update();
        }
        jdbc.sql("""
                INSERT INTO insider_fetch (ticker, fetched_at, total) VALUES (?, ?, ?)
                ON CONFLICT (ticker) DO UPDATE SET fetched_at = EXCLUDED.fetched_at, total = EXCLUDED.total
                """)
                .params(code, Timestamp.from(clock.instant()), page.total())
                .update();
    }

    private Insiders read(String code) {
        List<Transaction> rows = jdbc.sql("""
                SELECT trade_date, insider_name, action, nationality, shares_before, shares_after, shares_change,
                       percent_before, percent_after, price
                FROM insider_transaction WHERE ticker = ?
                ORDER BY trade_date DESC, id DESC LIMIT ?
                """)
                .params(code, SHOWN)
                .query((rs, n) -> new Transaction(rs.getDate("trade_date").toLocalDate(), rs.getString("insider_name"),
                        rs.getString("action"), rs.getString("nationality"), rs.getObject("shares_before", Long.class),
                        rs.getObject("shares_after", Long.class), rs.getObject("shares_change", Long.class),
                        rs.getBigDecimal("percent_before"), rs.getBigDecimal("percent_after"), rs.getBigDecimal("price")))
                .list();
        record Fetch(Instant at, Integer total) {
        }
        Fetch fetch = jdbc.sql("SELECT fetched_at, total FROM insider_fetch WHERE ticker = ?").param(code)
                .query((rs, n) -> new Fetch(rs.getTimestamp("fetched_at").toInstant(), rs.getObject("total", Integer.class)))
                .optional().orElse(null);
        return new Insiders(code, SOURCE, fetch == null ? null : fetch.at().atZone(YahooFinanceClient.JAKARTA).toOffsetDateTime(),
                fetch == null ? null : fetch.total(), rows);
    }

    private Instant lastFetch(String code) {
        return jdbc.sql("SELECT fetched_at FROM insider_fetch WHERE ticker = ?").param(code)
                .query((rs, n) -> rs.getTimestamp("fetched_at").toInstant()).optional().orElse(null);
    }

    private synchronized boolean takeFromBudget() {
        LocalDate today = ZonedDateTime.ofInstant(clock.instant(), YahooFinanceClient.JAKARTA).toLocalDate();
        if (!today.equals(budgetDay)) {
            budgetDay = today;
            usedToday = 0;
        }
        if (usedToday >= dailyBudget) return false;
        usedToday++;
        return true;
    }

    // ---------- parsing the source's display strings ----------

    static Long whole(String text) {
        BigDecimal value = decimal(text);
        return value == null ? null : value.longValue();
    }

    /** "1,915,000" -> 1915000, "+0.0008" -> 0.0008, "" or "-" -> null. */
    static BigDecimal decimal(String text) {
        if (text == null) return null;
        String cleaned = text.replace(",", "").replace("+", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("-")) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate date(String text) {
        try {
            return text == null ? null : LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String action(String text) {
        String value = text == null ? "" : text.trim().toLowerCase();
        return value.isEmpty() ? "unknown" : value.length() > 20 ? value.substring(0, 20) : value;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim().toLowerCase();
    }
}
