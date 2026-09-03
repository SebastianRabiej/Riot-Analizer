package com.riotanalizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveMatchDto(
        boolean inGame,
        Long gameId,
        Integer queueId,
        String gameMode,
        Long gameStartTime,
        Integer gameLengthSec,
        Integer mapId,
        List<LiveParticipantDto> participants
) {
    public static LiveMatchDto notInGame() {
        return new LiveMatchDto(false, null, null, null, null, null, null, null);
    }
}
