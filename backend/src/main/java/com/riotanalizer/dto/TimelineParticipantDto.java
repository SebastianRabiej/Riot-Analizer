package com.riotanalizer.dto;

/** Maps a timeline participant slot (1..10) to a puuid and their match identity. */
public record TimelineParticipantDto(
        int participantId,
        String puuid,
        int teamId,
        String championName,
        String teamPosition
) {
}
