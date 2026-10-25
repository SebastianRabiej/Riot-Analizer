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
 * @param next               the next players, in the exact order the scheduler will pick them
 * @param upcomingTracked    TRACKED players not due yet, soonest first
 * @param upcomingDiscovered DISCOVERED players not due yet, soonest first
 */
public record FetchQueueDto(
        String generatedAt,
        long schedulerTickMs,
        String lastRunAt,
        int lastRunChecked,
        Running running,
        long dueTracked,
        long dueDiscovered,
        List<FetchQueueEntryDto> next,
        List<FetchQueueEntryDto> upcomingTracked,
        List<FetchQueueEntryDto> upcomingDiscovered
) {

    public record Running(String puuid, String gameName, String tagLine, String trackingLevel,
                          String startedAt, String checkType) {
    }
}
