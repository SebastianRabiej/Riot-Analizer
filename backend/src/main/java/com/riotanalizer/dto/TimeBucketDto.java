package com.riotanalizer.dto;

/**
 * A single time/session bucket: {@code key} identifies the bucket (hour 0-23,
 * weekday 1-7 = Mon-Sun, or session position), {@code label} is a display
 * string, and games/wins/winRate are the tracked player's record in that bucket.
 */
public record TimeBucketDto(
        int key,
        String label,
        int games,
        int wins,
        double winRate
) {}
