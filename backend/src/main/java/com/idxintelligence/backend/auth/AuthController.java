package com.idxintelligence.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Account endpoints, called by the website's server (Server Actions), never by browsers directly.
 * Errors come back as {"error": CODE}; the website translates the code.
 */
@RestController
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record Credentials(String email, String password) {
    }

    public record ResetRequest(String email, String language) {
    }

    public record NewPassword(String token, String password) {
    }

    public record SessionResponse(String token, String email, Instant expiresAt) {
    }

    @PostMapping("/api/auth/register")
    public SessionResponse register(@RequestBody Credentials body, HttpServletRequest request) {
        return response(auth.register(body.email(), body.password(), clientKey(request)));
    }

    @PostMapping("/api/auth/login")
    public SessionResponse login(@RequestBody Credentials body, HttpServletRequest request) {
        return response(auth.login(body.email(), body.password(), clientKey(request)));
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(AuthFilter.bearer(authorization));
    }

    /** Always 202, whether or not the email has an account. */
    @PostMapping("/api/auth/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@RequestBody ResetRequest body, HttpServletRequest request) {
        auth.requestReset(body.email(), clientKey(request), "id".equals(body.language()));
    }

    @PostMapping("/api/auth/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@RequestBody NewPassword body) {
        auth.resetPassword(body.token(), body.password());
    }

    /** The logged-in user (requires a session, checked by AuthFilter). */
    @GetMapping("/api/me")
    public Map<String, String> me(HttpServletRequest request) {
        return Map.of("email", AuthFilter.currentUser(request).email());
    }

    private static SessionResponse response(AuthService.Session session) {
        return new SessionResponse(session.token(), session.user().email(), session.expiresAt());
    }

    /**
     * The visitor's address for rate limiting, forwarded by the website's server (the backend only sees
     * Vercel). It can be faked by direct callers, which is why limits also apply per email.
     */
    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Client-Ip");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return "ip:" + (ip.length() > 64 ? ip.substring(0, 64) : ip);
    }
}
