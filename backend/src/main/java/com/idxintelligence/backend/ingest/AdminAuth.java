package com.idxintelligence.backend.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards /api/admin endpoints with the ADMIN_TOKEN secret (sent as X-Admin-Token), so that once
 * deployed, strangers cannot spend provider quota. Every admin endpoint is refused when no token is set.
 */
@Component
public class AdminAuth {

    private final byte[] adminToken;

    public AdminAuth(@Value("${ADMIN_TOKEN:}") String adminToken) {
        this.adminToken = adminToken.trim().getBytes(StandardCharsets.UTF_8);
    }

    public void require(String token) {
        if (adminToken.length == 0 || token == null
                || !MessageDigest.isEqual(adminToken, token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
