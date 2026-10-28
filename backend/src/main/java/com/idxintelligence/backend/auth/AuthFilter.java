package com.idxintelligence.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Every /api endpoint needs a logged-in user ("Authorization: Bearer <session token>"), except health,
 * the account endpoints themselves and the admin endpoints (which have their own token). The backend's
 * address is public, so the data must be protected here and not only by the website.
 */
public class AuthFilter extends OncePerRequestFilter {

    static final String USER_ATTRIBUTE = AuthFilter.class.getName() + ".user";
    private static final List<String> PUBLIC = List.of("/api/health", "/api/auth/", "/api/admin/");

    private final AuthService auth;

    public AuthFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || "OPTIONS".equals(request.getMethod())
                || PUBLIC.stream().anyMatch(p -> p.endsWith("/") ? path.startsWith(p) : path.equals(p));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<AuthService.User> user = auth.user(bearer(request.getHeader("Authorization")));
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"LOGIN_REQUIRED\"}");
            return;
        }
        request.setAttribute(USER_ATTRIBUTE, user.get());
        chain.doFilter(request, response);
    }

    static String bearer(String header) {
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
    }

    /** The user AuthFilter let through; only valid on protected endpoints. */
    public static AuthService.User currentUser(HttpServletRequest request) {
        Object user = request.getAttribute(USER_ATTRIBUTE);
        if (user == null) throw new IllegalStateException("No logged-in user on this request");
        return (AuthService.User) user;
    }
}
