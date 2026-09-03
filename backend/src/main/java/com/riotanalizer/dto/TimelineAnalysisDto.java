package com.riotanalizer.dto;

import java.util.List;

/** Compact, cached reshaping of a Match-V5 timeline. Per-player analysis is
 *  derived from this on the client. */
public record TimelineAnalysisDto(
        String matchId,
        long frameIntervalMs,
        List<TimelineParticipantDto> participants,
        List<TimelineFrameDto> frames,
        List<TimelineEventDto> events
) {
}
