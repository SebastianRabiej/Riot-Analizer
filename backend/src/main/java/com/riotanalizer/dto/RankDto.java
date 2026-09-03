package com.riotanalizer.dto;

public record RankDto(
        String queueType,
        String tier,
        String rank,
        int leaguePoints,
        int wins,
        int losses,
        double winRate
) {
}
