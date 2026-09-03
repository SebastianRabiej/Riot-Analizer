package com.riotanalizer.dto;

/**
 * One row of the "with player" (duo) dashboard: a teammate the tracked player
 * has played alongside on the SAME team this season, with the shared record.
 * Because same-team players win and lose together, {@code wins}/{@code losses}
 * are simply the outcomes of those shared games.
 */
public record TeammateSummaryDto(
        String puuid,
        String gameName,
        String tagLine,
        int games,
        int wins,
        int losses,
        double winRate
) {}
