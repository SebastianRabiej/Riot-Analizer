package com.riotanalizer.dto;

import java.util.List;

/** One side (blue=100 / red=200) of a match, with its aggregate totals. */
public record MatchTeamDto(
        int teamId,
        boolean win,
        int kills,
        int deaths,
        int assists,
        int goldEarned,
        int damageToChampions,
        List<MatchParticipantDto> participants
) {
}
