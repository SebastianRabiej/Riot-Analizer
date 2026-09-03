package com.riotanalizer.dto;

public record ChampionStatDto(
        int championId,
        String championName,
        int games,
        int wins,
        int losses,
        double winRate,
        double avgKills,
        double avgDeaths,
        double avgAssists,
        double avgKda,
        double avgCsPerMin
) {
}
