package com.riotanalizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveParticipantDto(
        String puuid,
        String riotId,
        int championId,
        String championName,
        int teamId,
        int spell1Id,
        int spell2Id,
        String tier,
        String rank,
        Integer leaguePoints
) {
}
