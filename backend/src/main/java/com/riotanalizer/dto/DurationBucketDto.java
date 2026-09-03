package com.riotanalizer.dto;

/**
 * One game-length bucket: the tracked player's record in games whose duration
 * falls in [minSec, maxSec). {@code maxSec} is {@code Integer.MAX_VALUE} for the
 * open-ended top bucket.
 */
public record DurationBucketDto(
        int key,
        String label,
        int minSec,
        int maxSec,
        int games,
        int wins,
        double winRate
) {}
