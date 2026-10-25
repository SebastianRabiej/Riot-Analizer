package com.riotanalizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One player in a live game. Solo-queue rank: {@code tier/rank/leaguePoints} when
 * ranked, {@code unranked = true} when unranked, all absent when the lookup failed.
 */
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
        Integer leaguePoints,
        Boolean unranked
) {
}
