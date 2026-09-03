package com.riotanalizer.service;

import java.util.List;

/**
 * Pure helpers for form / session analysis. Dependency-free (only the JDK) so
 * they can be unit tested — and even compiled standalone — without Spring or a DB.
 */
public final class TrendMath {

    private TrendMath() {
    }

    /**
     * Signed current streak from a newest-first list of wins: a positive number
     * is a win streak of that length, a negative number a loss streak, 0 when
     * there are no games.
     */
    public static int currentStreak(List<Boolean> newestFirst) {
        if (newestFirst == null || newestFirst.isEmpty()) {
            return 0;
        }
        boolean first = Boolean.TRUE.equals(newestFirst.get(0));
        int n = 0;
        for (Boolean w : newestFirst) {
            if (Boolean.TRUE.equals(w) == first) {
                n++;
            } else {
                break;
            }
        }
        return first ? n : -n;
    }

    /** Longest run of wins (when {@code win} is true) or losses (false). */
    public static int longestStreak(List<Boolean> wins, boolean win) {
        if (wins == null) {
            return 0;
        }
        int best = 0;
        int cur = 0;
        for (Boolean w : wins) {
            if (Boolean.TRUE.equals(w) == win) {
                cur++;
                if (cur > best) {
                    best = cur;
                }
            } else {
                cur = 0;
            }
        }
        return best;
    }

    /**
     * Session position (1-based) for each game. Inputs are ordered ASC by start
     * time. A new session (position resets to 1) begins whenever the gap between
     * a game's start and the previous game's END exceeds {@code gapMs}.
     */
    public static int[] sessionPositions(long[] startAsc, int[] durationSec, long gapMs) {
        int n = startAsc.length;
        int[] pos = new int[n];
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                pos[i] = 1;
                continue;
            }
            long prevEnd = startAsc[i - 1] + Math.max(0, durationSec[i - 1]) * 1000L;
            if (startAsc[i] - prevEnd > gapMs) {
                pos[i] = 1;
            } else {
                pos[i] = pos[i - 1] + 1;
            }
        }
        return pos;
    }
}
