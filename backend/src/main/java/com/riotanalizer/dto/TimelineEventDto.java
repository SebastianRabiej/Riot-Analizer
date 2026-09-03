package com.riotanalizer.dto;

import java.util.List;

/**
 * A distilled timeline event. Only the fields relevant to the event type are
 * populated; the rest are null. Covers champion kills, elite monsters, buildings,
 * turret plates, wards and level-ups.
 */
public record TimelineEventDto(
        long timestampMs,
        String type,
        Integer participantId,
        Integer killerId,
        Integer victimId,
        List<Integer> assistIds,
        Integer teamId,
        String subType,
        String lane,
        Integer x,
        Integer y,
        Integer bounty,
        Integer shutdownBounty,
        String wardType,
        Integer level,
        Integer itemId
) {
}
