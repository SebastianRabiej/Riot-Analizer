package com.riotanalizer.dto;

import java.util.List;

/**
 * One player in the scheduler's queue and what it will do for them.
 *
 * @param position    1-based place in the "up next" order (null for not-yet-due players)
 * @param early       true when this place in "up next" is an early (spare-budget) check
 * @param nextCheckAt ISO; null = never checked (due now)
 * @param earlyCheckAt ISO; earliest time an early check may happen (null = any time)
 * @param checkType   HEAD_CHECK (1-call "anything new?") or BACKFILL (walking the season)
 * @param steps       planned steps, in order
 * @param minCalls    Riot calls if nothing new turns up
 * @param maxCalls    Riot calls if a full batch of new games turns up
 * @param reason      why the player is checked at this cadence
 * @param inGame      last known live-game state (tracked players only; null = unknown)
 */
public record FetchQueueEntryDto(
        Integer position,
        boolean early,
        String puuid,
        String gameName,
        String tagLine,
        int profileIconId,
        String trackingLevel,
        Integer discoveryDepth,
        String nextCheckAt,
        String earlyCheckAt,
        String newestMatchAt,
        String checkType,
        List<String> steps,
        int minCalls,
        int maxCalls,
        String reason,
        Boolean inGame
) {
}
