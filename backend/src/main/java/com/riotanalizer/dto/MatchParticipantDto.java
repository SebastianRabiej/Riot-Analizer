package com.riotanalizer.dto;

/** One participant on the full match scoreboard. */
public record MatchParticipantDto(
        String puuid,
        String gameName,
        String tagLine,
        int championId,
        String championName,
        int teamId,
        String teamPosition,
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
        int[] items,
        int summonerSpell1,
        int summonerSpell2
) {
}
