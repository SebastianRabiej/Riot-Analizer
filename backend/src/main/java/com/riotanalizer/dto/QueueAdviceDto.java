package com.riotanalizer.dto;

import java.util.List;

/**
 * "Should I queue again?" — a prospective, live read on whether now is a good time
 * to play, built from the same season data as the Trends tab (session position,
 * time-of-day/weekday win rates, current streak) but pointed at the *next* game
 * rather than the past. {@code signal} is GO / CAUTION / STOP; {@code predictedWinRate}
 * is the blended, sample-shrunk estimate for that next game.
 */
public record QueueAdviceDto(
        String gameName,
        String tagLine,
        String signal,
        double predictedWinRate,
        int sessionPositionNext,
        long minutesSinceLastGame,
        int sampleSize,
        List<ReasonDto> reasons
) {}
