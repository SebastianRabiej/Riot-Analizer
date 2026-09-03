package com.riotanalizer.dto;

import java.util.List;

/**
 * The Insights payload: current form streaks plus a ranked list of highlights.
 * {@code currentStreak} is signed (positive = win streak, negative = loss streak).
 */
public record InsightsDto(
        String gameName,
        String tagLine,
        int gamesAnalyzed,
        int currentStreak,
        int longestWinStreak,
        int longestLossStreak,
        List<InsightDto> highlights
) {}
