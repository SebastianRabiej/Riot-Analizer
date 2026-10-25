package com.riotanalizer.dto;

import java.util.List;

/**
 * Riot API budget right now. All "...InMs" values are relative to the
 * snapshot's {@code generatedAt}, so the UI can count down locally.
 *
 * @param shortWindow our own count, short window (default 20 / 1 s)
 * @param longWindow  our own count, long window (default 100 / 120 s)
 * @param priorities  per-priority budget, highest priority first
 * @param riot        Riot's own count from response headers; null before the first call
 */
public record RateLimitDto(
        Window shortWindow,
        Window longWindow,
        List<Priority> priorities,
        RiotView riot
) {

    /** @param nextExpiryInMs ms until the oldest call in the window drops out (null when empty) */
    public record Window(int used, int limit, long windowMs, Long nextExpiryInMs) {
    }

    /**
     * @param priority  REFRESH / ANALYSIS / TRACKED_HISTORY / DISCOVERED_HISTORY
     * @param longLimit long-window calls this priority may use (limit minus reserve)
     * @param freeInMs  ms until it could make a call on budget alone (0 = now)
     * @param waiting   calls of this priority blocked waiting for a permit
     */
    public record Priority(String priority, int longLimit, int reserve, long freeInMs, int waiting) {
    }

    /**
     * @param observedAt       when the counts below were reported (ISO)
     * @param appWindows       Riot's app-level windows as of that response
     * @param rateLimitedCount 429s received since the server started
     */
    public record RiotView(String observedAt, List<RiotWindow> appWindows, long rateLimitedCount,
                           String lastRateLimitedAt, Integer lastRetryAfterSec, String lastRateLimitType) {
    }

    public record RiotWindow(int windowSec, int count, int limit) {
    }
}
