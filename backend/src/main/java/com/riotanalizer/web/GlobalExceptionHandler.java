package com.riotanalizer.web;

import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.exception.RateLimitedException;
import com.riotanalizer.exception.RiotApiException;
import com.riotanalizer.security.AdminAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimited(RateLimitedException ex, HttpServletRequest req) {
        // 429 from Riot is surfaced as 503 with a retry note.
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "Upstream rate limit hit, please retry shortly", req);
    }

    @ExceptionHandler(RiotApiException.class)
    public ResponseEntity<Map<String, Object>> handleRiot(RiotApiException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), req);
    }

    /**
     * Method-security denials (@PreAuthorize) surface here rather than in the
     * security filter chain; without this they'd fall into the generic 500.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public ResponseEntity<Map<String, Object>> handleDenied(RuntimeException ex, HttpServletRequest req) {
        return AdminAccess.isAdmin()
                ? build(HttpStatus.FORBIDDEN, "Forbidden", req)
                : build(HttpStatus.UNAUTHORIZED, "Admin login required", req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBadBody(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() == null ? "Internal error" : ex.getMessage(), req);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error, HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("status", status.value());
        body.put("path", req.getRequestURI());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
