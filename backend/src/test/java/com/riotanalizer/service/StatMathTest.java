package com.riotanalizer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatMathTest {

    @Test
    void kdaUsesMaxOneForZeroDeaths() {
        // (10 + 5) / max(1, 0) = 15
        assertEquals(15.0, StatMath.kda(10, 0, 5), 1e-9);
    }

    @Test
    void kdaNormalCase() {
        // (5 + 7) / 4 = 3.0
        assertEquals(3.0, StatMath.kda(5, 4, 7), 1e-9);
    }

    @Test
    void winRateIsPercentage() {
        assertEquals(50.0, StatMath.winRate(5, 10), 1e-9);
        assertEquals(0.0, StatMath.winRate(0, 0), 1e-9);
        assertEquals(33.33, StatMath.winRate(1, 3), 1e-9);
    }

    @Test
    void csPerMinComputesFromDurationSeconds() {
        // 200 cs over 1200s (20 min) = 10.0
        assertEquals(10.0, StatMath.csPerMin(200, 1200), 1e-9);
        assertEquals(0.0, StatMath.csPerMin(200, 0), 1e-9);
    }

    @Test
    void avgRoundsToTwoDecimals() {
        assertEquals(3.33, StatMath.avg(10, 3), 1e-9);
        assertEquals(0.0, StatMath.avg(10, 0), 1e-9);
    }

    @Test
    void shareIsPercentageOfTotal() {
        // an even 1/5 contribution -> 20%
        assertEquals(20.0, StatMath.share(200, 1000), 1e-9);
        assertEquals(0.0, StatMath.share(5, 0), 1e-9);
        assertEquals(33.33, StatMath.share(1, 3), 1e-9);
    }
}
