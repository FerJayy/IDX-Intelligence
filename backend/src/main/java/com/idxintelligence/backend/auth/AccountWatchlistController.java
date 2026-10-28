package com.idxintelligence.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** "My list", stored per account so it follows the user to every device. */
@RestController
public class AccountWatchlistController {

    public static final int MAX = 30;

    private final JdbcClient jdbc;

    public AccountWatchlistController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Tickers(List<String> tickers) {
    }

    /** Oldest first, the order stocks were added. */
    @GetMapping("/api/me/watchlist")
    public Tickers list(HttpServletRequest request) {
        return new Tickers(tickers(AuthFilter.currentUser(request).id()));
    }

    /** Adds a stock; unknown stocks are 404, a full list is 409. */
    @PutMapping("/api/me/watchlist/{ticker}")
    public Tickers add(@PathVariable String ticker, HttpServletRequest request) {
        long user = AuthFilter.currentUser(request).id();
        String code = ticker.toUpperCase(Locale.ROOT);
        boolean known = jdbc.sql("SELECT count(*) FROM stock WHERE ticker = ? AND active").param(code).query(Integer.class).single() > 0;
        if (!known) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "UNKNOWN_STOCK");
        List<String> current = tickers(user);
        if (!current.contains(code)) {
            if (current.size() >= MAX) throw new ResponseStatusException(HttpStatus.CONFLICT, "LIST_FULL");
            jdbc.sql("INSERT INTO user_watchlist (user_id, ticker) VALUES (?, ?) ON CONFLICT DO NOTHING").params(user, code).update();
        }
        return new Tickers(tickers(user));
    }

    @DeleteMapping("/api/me/watchlist/{ticker}")
    public Tickers remove(@PathVariable String ticker, HttpServletRequest request) {
        long user = AuthFilter.currentUser(request).id();
        jdbc.sql("DELETE FROM user_watchlist WHERE user_id = ? AND ticker = ?").params(user, ticker.toUpperCase(Locale.ROOT)).update();
        return new Tickers(tickers(user));
    }

    /** Adds a browser's old cookie list on first login; known stocks only, up to the maximum. */
    @PostMapping("/api/me/watchlist/merge")
    public Tickers merge(@RequestBody Tickers body, HttpServletRequest request) {
        long user = AuthFilter.currentUser(request).id();
        List<String> current = tickers(user);
        int room = MAX - current.size();
        for (String raw : body.tickers() == null ? List.<String>of() : body.tickers()) {
            if (room <= 0) break;
            String code = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Z]{4}") || current.contains(code)) continue;
            int added = jdbc.sql("""
                    INSERT INTO user_watchlist (user_id, ticker)
                    SELECT ?, ticker FROM stock WHERE ticker = ? AND active
                    ON CONFLICT DO NOTHING
                    """).params(user, code).update();
            if (added > 0) room--;
        }
        return new Tickers(tickers(user));
    }

    private List<String> tickers(long user) {
        return jdbc.sql("SELECT ticker FROM user_watchlist WHERE user_id = ? ORDER BY added_at, ticker")
                .param(user).query(String.class).list();
    }
}
