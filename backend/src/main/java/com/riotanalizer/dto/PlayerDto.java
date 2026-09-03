package com.riotanalizer.dto;

public record PlayerDto(
        String puuid,
        String gameName,
        String tagLine,
        int summonerLevel,
        int profileIconId,
        RankDto soloRank,
        RankDto flexRank,
        boolean tracked,
        String lastFetched
) {
}
