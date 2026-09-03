package com.riotanalizer.exception;

/**
 * Wraps any non-404, non-429 error from the Riot API.
 */
public class RiotApiException extends RuntimeException {
    private final int status;

    public RiotApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
