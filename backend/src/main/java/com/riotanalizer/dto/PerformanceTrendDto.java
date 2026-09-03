package com.riotanalizer.dto;

import java.util.List;

/**
 * The tracked player's per-game metric series for the current season, oldest
 * game first so it can be charted left-to-right. Metric selection, per-role
 * filtering and trendline smoothing are done on the client from these points.
 */
public record PerformanceTrendDto(
        String gameName,
        String tagLine,
        int totalGames,
        List<PerfPointDto> points
) {
}
