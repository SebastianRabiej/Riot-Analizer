package com.riotanalizer.dto;

import java.util.List;

/**
 * What the background scheduler is doing now and will do next.
 *
 * @param generatedAt        server time of this snapshot (ISO)
 * @param schedulerTickMs    pause between scheduler runs when nothing is due
 * @param lastRunAt          when the last scheduler run finished (ISO, null before the first)
 * @param lastRunChecked     players checked by that run
 * @param running            the player being checked right now, or null when idle
 * @param dueTracked         TRACKED players due now (excluding the running one)
 * @param dueDiscovered      DISCOVERED players due now (excluding the running one)
 * @param earlyTracked       TRACKED players not due yet but eligible for an early check
 * @param earlyDiscovered    DISCOVERED players not due yet but eligible for an early check
 * @param earlyChecks        whether spare budget currently allows early checks, and the thresholds
 * @param next               the next players, in the exact order the scheduler will pick them
 *                           right now (due players, plus early checks while the budget allows)
 * @param upcomingTracked    TRACKED players not due yet, soonest first
 * @param upcomingDiscovered DISCOVERED players not due yet, soonest first
 * @param rateLimit          Riot API budget right now
 */
public record FetchQueueDto(
        String generatedAt,
        long schedulerTickMs,
        String lastRunAt,
        int lastRunChecked,
        Running running,
        long dueTracked,
        long dueDiscovered,
        long earlyTracked,
        long earlyDiscovered,
        EarlyChecks earlyChecks,
        List<FetchQueueEntryDto> next,
        List<FetchQueueEntryDto> upcomingTracked,
        List<FetchQueueEntryDto> upcomingDiscovered,
        RateLimitDto rateLimit
) {

    public record Running(String puuid, String gameName, String tagLine, String trackingLevel,
                          String startedAt, String checkType, boolean early) {
    }

    /**
     * Early (spare-budget) checks right now.
     *
     * @param longUsed          long-window calls used right now
     * @param trackedMinFree    long-window permits that must be free for an early TRACKED check
     * @param discoveredMinFree same for DISCOVERED
     * @param trackedOpen       an early TRACKED check would be allowed right now
     * @param discoveredOpen    an early DISCOVERED check would be allowed right now
     * @param trackedMinGapMs   minimum time between two checks of one tracked player
     * @param discoveredMinGapMs same for discovered players
     */
    public record EarlyChecks(boolean enabled, int longUsed, int longLimit, int trackedMinFree,
                              int discoveredMinFree, boolean trackedOpen, boolean discoveredOpen,
                              long trackedMinGapMs, long discoveredMinGapMs) {
    }
}
