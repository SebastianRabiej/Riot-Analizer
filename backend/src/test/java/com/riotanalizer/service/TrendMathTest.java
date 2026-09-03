package com.riotanalizer.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TrendMathTest {

    @Test
    void currentStreakSignedFromNewestFirst() {
        // newest-first: W W W L ...  -> +3 win streak
        assertEquals(3, TrendMath.currentStreak(List.of(true, true, true, false, true)));
        // newest-first: L L ...      -> -2 loss streak
        assertEquals(-2, TrendMath.currentStreak(List.of(false, false, true)));
        assertEquals(0, TrendMath.currentStreak(List.of()));
    }

    @Test
    void longestStreakForWinsAndLosses() {
        List<Boolean> f = List.of(true, true, false, true, true, true, false);
        assertEquals(3, TrendMath.longestStreak(f, true));
        assertEquals(1, TrendMath.longestStreak(f, false));
        assertEquals(0, TrendMath.longestStreak(List.of(), true));
    }

    @Test
    void sessionPositionsResetAfterGap() {
        long min = 60_000L;
        // three games 20 min long, 5 min apart -> one session 1,2,3
        long[] start = { 0, 25 * min, 50 * min, 200 * min };
        int[] dur = { 20 * 60, 20 * 60, 20 * 60, 20 * 60 };
        // gap threshold 30 min; the 4th game starts long after -> new session
        int[] pos = TrendMath.sessionPositions(start, dur, 30 * min);
        assertArrayEquals(new int[] { 1, 2, 3, 1 }, pos);
    }

    @Test
    void sessionPositionsSingleGame() {
        assertArrayEquals(new int[] { 1 },
                TrendMath.sessionPositions(new long[] { 123L }, new int[] { 600 }, 1800_000L));
    }
}
