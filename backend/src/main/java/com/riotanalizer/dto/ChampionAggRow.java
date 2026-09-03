package com.riotanalizer.dto;

/**
 * Raw aggregation row for enemy champions faced (internal projection from JPQL).
 * Sums are the tracked player's totals across games where that champion was an enemy.
 */
public record ChampionAggRow(
        Integer championId,
        String championName,
        Long games,
        Long wins,
        Long kills,
        Long deaths,
        Long assists
) {}
