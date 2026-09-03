package com.riotanalizer.dto;

/**
 * Per-position (role/lane) performance for the tracked player this season.
 * {@code position} is the Match-V5 {@code teamPosition} (TOP, JUNGLE, MIDDLE,
 * BOTTOM, UTILITY), or "NONE" for games without a stored position (e.g. ARAM).
 */
public record RoleStatDto(
        String position,
        int games,
        int wins,
        int losses,
        double winRate,
        double avgKills,
        double avgDeaths,
        double avgAssists,
        double avgKda,
        double avgCsPerMin,
        double avgVisionScore
) {}
