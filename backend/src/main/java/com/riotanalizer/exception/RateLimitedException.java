package com.riotanalizer.exception;

/**
 * Raised when Riot returns HTTP 429. Surfaced to clients as HTTP 503.
 */
public class RateLimitedException extends RuntimeException {
    public RateLimitedException(String message) {
        super(message);
    }
}
