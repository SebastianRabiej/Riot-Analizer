package com.riotanalizer.dto;

public record H2HMatchDto(
        String matchId,
        long gameCreation,
        boolean sameTeam,
        String playerChampionName,
        String opponentChampionName,
        boolean playerWin,
        int playerKills,
        int playerDeaths,
        int playerAssists,
        int opponentKills,
        int opponentDeaths,
        int opponentAssists,
        String queueName
) {
}
