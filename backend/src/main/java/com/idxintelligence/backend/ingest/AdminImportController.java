package com.idxintelligence.backend.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Manual trigger for the price import. Protected by the ADMIN_TOKEN secret so that,
 * once deployed, strangers cannot make the server hammer Yahoo. Disabled when no token is set.
 */
@RestController
public class AdminImportController {

    private final PriceImportService importService;
    private final String adminToken;

    public AdminImportController(PriceImportService importService,
                                 @Value("${ADMIN_TOKEN:}") String adminToken) {
        this.importService = importService;
        this.adminToken = adminToken;
    }

    @PostMapping("/api/admin/import/prices")
    public PriceImportService.ImportResult importPrices(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (adminToken.isBlank() || token == null
                || !MessageDigest.isEqual(adminToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return importService.importAll();
    }
}
