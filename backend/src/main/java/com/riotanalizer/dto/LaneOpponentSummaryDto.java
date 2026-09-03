package com.riotanalizer.dto;

/**
 * One row of the "lane opponents" breakdown: an enemy champion the tracked
 * player has faced in the SAME position (their actual lane opponent) this
 * season, with the player's record and average KDA in those games.
 */
public record LaneOpponentSummaryDto(
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
