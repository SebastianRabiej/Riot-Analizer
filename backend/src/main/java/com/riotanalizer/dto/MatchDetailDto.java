package com.riotanalizer.dto;

import java.util.List;

/** Full detail for a single match: metadata plus both teams' scoreboards. */
public record MatchDetailDto(
        String matchId,
        int queueId,
        String queueName,
        long gameCreation,
        int gameDurationSec,
        Integer mapId,
        String gameVersion,
        List<MatchTeamDto> teams
) {
}
