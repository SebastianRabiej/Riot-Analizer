package com.riotanalizer.exception;

import java.util.Locale;

/**
 * Wraps any non-404, non-429 error from the Riot API.
 *
 * <p>{@link #getRiotMessage()} carries Riot's own explanation from the error
 * body ({@code status.message}), e.g. {@code "Bad Request - Exception decrypting ..."}.
 */
public class RiotApiException extends RuntimeException {
    private final int status;
    private final String riotMessage;

    public RiotApiException(int status, String message) {
        this(status, message, null);
    }

    public RiotApiException(int status, String message, String riotMessage) {
        super(message);
        this.status = status;
        this.riotMessage = riotMessage;
    }

    public int getStatus() {
        return status;
    }

    /** Riot's message from the error body, or {@code null} if there was none. */
    public String getRiotMessage() {
        return riotMessage;
    }

    /**
     * True when Riot rejected an encrypted id (PUUID / summonerId) that was
     * issued for a different API key's application — e.g. after switching
     * from a development key to a personal or production key.
     */
    public boolean isDecryptionError() {
        return isDecryptionError(status, riotMessage);
    }

    public static boolean isDecryptionError(int status, String riotMessage) {
        return status == 400 && riotMessage != null
                && riotMessage.toLowerCase(Locale.ROOT).contains("decrypt");
    }
}
