package com.riotanalizer.dto;

/**
 * One row of the "vs champion" dashboard: an enemy champion the tracked player
 * has faced this season, with the player's record and average KDA in those games.
 */
public record ChampionMatchupSummaryDto(
        Integer championId,
        String championName,
        int games,
        int wins,
        int losses,
        double winRate,
        double avgKills,
        double avgDeaths,
        double avgAssists,
        double avgKda
) {}
