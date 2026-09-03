package com.riotanalizer.dto;

import java.util.List;

/** A single ~1-minute timeline frame: all participants' state at that instant. */
public record TimelineFrameDto(
        long timestampMs,
        List<TimelineParticipantFrameDto> participants
) {
}
