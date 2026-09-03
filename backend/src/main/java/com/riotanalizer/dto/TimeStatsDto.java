package com.riotanalizer.dto;

import java.util.List;

/**
 * "When do you play best" breakdown. {@code byHour} has 24 buckets (local time),
 * {@code byWeekday} has 7 (Mon-Sun), and {@code bySession} buckets games by their
 * position within a play session (consecutive games with only short gaps between
 * them), which is what surfaces tilt / diminishing returns.
 */
public record TimeStatsDto(
        String gameName,
        String tagLine,
        int totalGames,
        List<TimeBucketDto> byHour,
        List<TimeBucketDto> byWeekday,
        List<TimeBucketDto> bySession
) {}
