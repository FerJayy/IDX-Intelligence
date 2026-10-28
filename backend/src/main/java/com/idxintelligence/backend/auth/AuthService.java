package com.idxintelligence.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Email + password accounts. Passwords are BCrypt hashes; session and reset tokens are random 256-bit
 * values that only exist in the user's cookie or email, while the database keeps their SHA-256 hash.
 * No email verification (the site is for a few friends); password reset goes to the account's email.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final Duration SESSION_VALID_FOR = Duration.ofDays(30);
    public static final Duration RESET_VALID_FOR = Duration.ofMinutes(30);
    static final int MIN_PASSWORD = 8;
    /** BCrypt only uses the first 72 bytes; longer passwords are refused rather than silently cut. */
    static final int MAX_PASSWORD_BYTES = 72;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$");

    private final JdbcClient jdbc;
    private final ResetMailer mailer;
    private final Clock clock;
    private final String siteUrl;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    private final SecureRandom random = new SecureRandom();
    /** Used for unknown emails so a login takes as long whether or not the account exists. */
    private final String dummyHash;
    private final AttemptLimiter loginFailures;
    private final AttemptLimiter resetRequests;
    private final AttemptLimiter registrations;
    /** Session lookups for 60 s, so one page's several API calls don't each hit the database. */
    private final java.util.Map<String, CachedUser> sessions = new java.util.concurrent.ConcurrentHashMap<>();
    static final Duration SESSION_CACHE = Duration.ofSeconds(60);

    private record CachedUser(User user, Instant until) {
    }

    public AuthService(JdbcClient jdbc, ResetMailer mailer, Clock clock,
                       @Value("${app.site-url:http://localhost:3000}") String siteUrl) {
        this.jdbc = jdbc;
        this.mailer = mailer;
        this.clock = clock;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
        this.dummyHash = encoder.encode("not-a-real-password");
        this.loginFailures = new AttemptLimiter(8, Duration.ofMinutes(15), clock);
        this.resetRequests = new AttemptLimiter(3, Duration.ofHours(1), clock);
        this.registrations = new AttemptLimiter(5, Duration.ofHours(1), clock);
    }

    public record User(long id, String email) {
    }

    /** @param token the session token for the cookie; shown to the client once, never stored in clear */
    public record Session(String token, User user, Instant expiresAt) {
    }

    @Transactional
    public Session register(String rawEmail, String password, String clientKey) {
        String email = normalize(rawEmail);
        if (!EMAIL.matcher(email).matches() || email.length() > 254) throw error(HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        checkPassword(password);
        if (!registrations.allowed(clientKey)) throw error(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS");
        registrations.record(clientKey);
        record Existing(long id, String hash) {
        }
        Optional<Existing> existing = jdbc.sql("SELECT id, password_hash FROM app_user WHERE email = ?").param(email)
                .query((rs, n) -> new Existing(rs.getLong("id"), rs.getString("password_hash"))).optional();
        if (existing.isPresent()) {
            // A repeated submit (double click, or a slow first try while the server woke up) already created
            // the account: the same password simply logs in. A different password can't take it over.
            if (encoder.matches(password, existing.get().hash())) return newSession(new User(existing.get().id(), email));
            throw error(HttpStatus.CONFLICT, "EMAIL_TAKEN");
        }
        long id = jdbc.sql("INSERT INTO app_user (email, password_hash) VALUES (?, ?) RETURNING id")
                .params(email, encoder.encode(password)).query(Long.class).single();
        return newSession(new User(id, email));
    }

    public Session login(String rawEmail, String password, String clientKey) {
        String email = normalize(rawEmail);
        String emailKey = "email:" + email;
        if (!loginFailures.allowed(emailKey) || !loginFailures.allowed(clientKey)) {
            throw error(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS");
        }
        record Row(long id, String hash) {
        }
        Optional<Row> row = jdbc.sql("SELECT id, password_hash FROM app_user WHERE email = ?").param(email)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("password_hash"))).optional();
        boolean ok = password != null && encoder.matches(password, row.map(Row::hash).orElse(dummyHash)) && row.isPresent();
        if (!ok) {
            loginFailures.record(emailKey);
            loginFailures.record(clientKey);
            throw error(HttpStatus.UNAUTHORIZED, "WRONG_LOGIN");
        }
        loginFailures.clear(emailKey);
        jdbc.sql("UPDATE app_user SET last_login_at = ? WHERE id = ?").params(Timestamp.from(clock.instant()), row.get().id()).update();
        return newSession(new User(row.get().id(), email));
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        sessions.remove(hash(token));
        jdbc.sql("DELETE FROM user_session WHERE token_hash = ?").param(hash(token)).update();
    }

    /** The logged-in user for a session token, or empty when it is unknown or expired. */
    public Optional<User> user(String token) {
        if (token == null || token.isBlank() || token.length() > 100) return Optional.empty();
        String key = hash(token);
        Instant now = clock.instant();
        CachedUser cached = sessions.get(key);
        if (cached != null && cached.until().isAfter(now)) return Optional.of(cached.user());
        if (sessions.size() > 5_000) sessions.clear();
        Optional<User> found = jdbc.sql("""
                SELECT u.id, u.email FROM user_session s JOIN app_user u ON u.id = s.user_id
                WHERE s.token_hash = ? AND s.expires_at > ?
                """)
                .params(key, Timestamp.from(now))
                .query((rs, n) -> new User(rs.getLong("id"), rs.getString("email")))
                .optional();
        found.ifPresentOrElse(u -> sessions.put(key, new CachedUser(u, now.plus(SESSION_CACHE))), () -> sessions.remove(key));
        return found;
    }

    /**
     * Emails a reset link if the account exists. Callers always get the same answer, so the form can't be
     * used to find out who has an account. Limited per email and per client to stop email flooding.
     */
    public void requestReset(String rawEmail, String clientKey, boolean indonesian) {
        String email = normalize(rawEmail);
        String emailKey = "reset:" + email;
        if (!resetRequests.allowed(emailKey) || !resetRequests.allowed(clientKey)) {
            throw error(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS");
        }
        resetRequests.record(emailKey);
        resetRequests.record(clientKey);
        Optional<Long> userId = jdbc.sql("SELECT id FROM app_user WHERE email = ?").param(email).query(Long.class).optional();
        if (userId.isEmpty()) return;

        String token = newToken();
        Instant now = clock.instant();
        jdbc.sql("INSERT INTO password_reset (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)")
                .params(hash(token), userId.get(), Timestamp.from(now), Timestamp.from(now.plus(RESET_VALID_FOR)))
                .update();
        String link = siteUrl + (indonesian ? "/id" : "/en") + "/reset-password?token=" + token;
        try {
            mailer.sendReset(email, link, indonesian);
        } catch (RuntimeException e) {
            log.warn("Sending the reset email failed: {}", e.getMessage());
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_FAILED");
        }
    }

    /** Sets a new password from a valid, unused reset link and signs the account out everywhere. */
    @Transactional
    public void resetPassword(String token, String password) {
        checkPassword(password);
        if (token == null || token.isBlank() || token.length() > 100) throw error(HttpStatus.BAD_REQUEST, "INVALID_LINK");
        Instant now = clock.instant();
        Optional<Long> userId = jdbc.sql("""
                UPDATE password_reset SET used_at = ?
                WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?
                RETURNING user_id
                """)
                .params(Timestamp.from(now), hash(token), Timestamp.from(now))
                .query(Long.class)
                .optional();
        if (userId.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "INVALID_LINK");
        jdbc.sql("UPDATE app_user SET password_hash = ? WHERE id = ?").params(encoder.encode(password), userId.get()).update();
        jdbc.sql("DELETE FROM user_session WHERE user_id = ?").param(userId.get()).update();
        long id = userId.get();
        sessions.values().removeIf(c -> c.user().id() == id);   // signed out everywhere, immediately
        jdbc.sql("UPDATE password_reset SET used_at = ? WHERE user_id = ? AND used_at IS NULL")
                .params(Timestamp.from(now), userId.get()).update();
        loginFailures.clear("email:" + jdbc.sql("SELECT email FROM app_user WHERE id = ?").param(userId.get()).query(String.class).single());
    }

    private Session newSession(User user) {
        String token = newToken();
        Instant expires = clock.instant().plus(SESSION_VALID_FOR);
        jdbc.sql("INSERT INTO user_session (token_hash, user_id, expires_at) VALUES (?, ?, ?)")
                .params(hash(token), user.id(), Timestamp.from(expires)).update();
        // Housekeeping: expired sessions and old reset links of this user.
        jdbc.sql("DELETE FROM user_session WHERE user_id = ? AND expires_at < ?").params(user.id(), Timestamp.from(clock.instant())).update();
        return new Session(token, user, expires);
    }

    private void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) throw error(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_SHORT");
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) throw error(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG");
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** The reason code is the response's message; the frontend translates it. */
    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
