package com.riotanalizer.dto;

import java.util.List;

/**
 * "Win rate by game length" for the tracked player this season: do you stomp
 * early and fade, or grind out long games? Built purely from stored match
 * durations and outcomes — no timeline data. {@code avgWinDurationSec} vs
 * {@code avgLossDurationSec} shows whether your wins are faster than your losses.
 */
public record GameLengthStatsDto(
        String gameName,
        String tagLine,
        int total,
        List<DurationBucketDto> buckets,
        double avgWinDurationSec,
        double avgLossDurationSec
) {}
