package com.riotanalizer.dto;

/** One participant's state at a single timeline frame. */
public record TimelineParticipantFrameDto(
        int participantId,
        int totalGold,
        int xp,
        int cs,
        int level,
        int x,
        int y,
        int damage,
        int currentGold
) {
}
