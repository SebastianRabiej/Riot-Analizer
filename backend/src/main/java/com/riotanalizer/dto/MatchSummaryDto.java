package com.riotanalizer.dto;

public record MatchSummaryDto(
        String matchId,
        int queueId,
        String queueName,
        long gameCreation,
        int gameDurationSec,
        int championId,
        String championName,
        boolean win,
        int kills,
        int deaths,
        int assists,
        double kda,
        int cs,
        double csPerMin,
        int visionScore,
        int damageToChampions,
        int goldEarned,
        String teamPosition,
        int[] items,
        int summonerSpell1,
        int summonerSpell2
) {
}
