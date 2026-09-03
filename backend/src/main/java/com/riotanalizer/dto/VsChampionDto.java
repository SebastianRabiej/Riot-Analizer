package com.riotanalizer.dto;

import java.util.List;

public record VsChampionDto(
        String gameName,
        String tagLine,
        String championName,
        int gamesAgainst,
        int wins,
        int losses,
        double winRate,
        double avgKills,
        double avgDeaths,
        double avgAssists,
        double avgKda,
        List<VsChampionMatchDto> matches
) {
}
