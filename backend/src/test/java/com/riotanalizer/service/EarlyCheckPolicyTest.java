package com.riotanalizer.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.riotanalizer.domain.TrackingLevel.DISCOVERED;
import static com.riotanalizer.domain.TrackingLevel.TRACKED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarlyCheckPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    private final EarlyCheckPolicy policy =
            new EarlyCheckPolicy(true, Duration.ofMinutes(2), Duration.ofMinutes(30), 0.4, 0.6);

    @Test
    void trackedEarlyChecksNeedLessHeadroomThanDiscovered() {
        assertEquals(40, policy.minFree(TRACKED, 100));
        assertEquals(60, policy.minFree(DISCOVERED, 100));

        assertTrue(policy.budgetAllows(TRACKED, 60, 100));
        assertFalse(policy.budgetAllows(TRACKED, 61, 100));
        assertTrue(policy.budgetAllows(DISCOVERED, 40, 100));
        assertFalse(policy.budgetAllows(DISCOVERED, 41, 100));
    }

    @Test
    void idleBudgetAllowsBoth() {
        assertTrue(policy.budgetAllows(TRACKED, 0, 100));
        assertTrue(policy.budgetAllows(DISCOVERED, 0, 100));
    }

    @Test
    void disabledPolicyNeverAllows() {
        EarlyCheckPolicy off = new EarlyCheckPolicy(false, Duration.ofMinutes(2), Duration.ofMinutes(30), 0.4, 0.6);
        assertFalse(off.budgetAllows(TRACKED, 0, 100));
    }

    @Test
    void discoveredNeverGetsMoreBudgetThanTracked() {
        EarlyCheckPolicy odd = new EarlyCheckPolicy(true, Duration.ZERO, Duration.ZERO, 0.5, 0.2);
        assertEquals(50, odd.minFree(DISCOVERED, 100));
    }

    @Test
    void scalesWithProductionLimits() {
        assertEquals(12_000, policy.minFree(TRACKED, 30_000));
    }

    @Test
    void minGapPerLevel() {
        assertEquals(NOW.plus(Duration.ofMinutes(2)), policy.earlyCheckAt(TRACKED, NOW));
        assertEquals(NOW.plus(Duration.ofMinutes(30)), policy.earlyCheckAt(DISCOVERED, NOW));
    }
}
