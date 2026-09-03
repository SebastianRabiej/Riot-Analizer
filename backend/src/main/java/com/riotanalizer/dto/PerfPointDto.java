package com.riotanalizer.dto;

/**
 * One game's per-match metrics, for the "performance over time" trend chart.
 * Carries {@code teamPosition} so the client can filter a metric to a single
 * role (a support's CS/min is not comparable to a mid's — they must be split).
 */
public record PerfPointDto(
        long gameCreation,
        String matchId,
        int championId,
        String championName,
        String teamPosition,
        boolean win,
        int kills,
        int deaths,
        int assists,
        double kda,
        int cs,
        double csPerMin,
        int visionScore,
        int damageToChampions,
        int goldEarned,
        int gameDurationSec,
        int queueId
) {
}
