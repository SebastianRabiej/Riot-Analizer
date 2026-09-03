package com.riotanalizer.dto;

public record VsChampionMatchDto(
        String matchId,
        long gameCreation,
        String playerChampionName,
        boolean win,
        int kills,
        int deaths,
        int assists,
        String queueName
) {
}
