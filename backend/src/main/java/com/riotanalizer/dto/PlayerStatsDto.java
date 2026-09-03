package com.riotanalizer.dto;

import java.util.List;

public record PlayerStatsDto(
        String gameName,
        String tagLine,
        int gamesPlayed,
        int wins,
        int losses,
        double winRate,
        double avgKills,
        double avgDeaths,
        double avgAssists,
        double avgKda,
        double avgCsPerMin,
        double avgVisionScore,
        double avgDamageToChampions,
        double avgGoldEarned,
        double avgGameDurationSec,
        List<Boolean> recentForm,
        List<ChampionStatDto> championStats
) {
}
