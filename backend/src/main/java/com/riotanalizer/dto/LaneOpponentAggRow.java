package com.riotanalizer.dto;

/**
 * Raw aggregation row for lane opponents (enemy champion in the same position).
 * Sums are the tracked player's totals across those lane matchups.
 */
public record LaneOpponentAggRow(
        Integer championId,
        String championName,
        Long games,
        Long wins,
        Long kills,
        Long deaths,
        Long assists
) {}
