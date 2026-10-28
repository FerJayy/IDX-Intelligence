package com.idxintelligence.backend.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Account errors as {"error": CODE} (e.g. WRONG_LOGIN, EMAIL_TAKEN); the website translates the code. */
@RestControllerAdvice(assignableTypes = {AuthController.class, AccountWatchlistController.class})
class AuthErrors {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> error(ResponseStatusException e) {
        String code = e.getReason() == null ? "ERROR" : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", code));
    }
}
