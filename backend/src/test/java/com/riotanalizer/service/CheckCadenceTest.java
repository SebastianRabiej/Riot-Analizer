package com.riotanalizer.service;

import com.riotanalizer.domain.TrackingLevel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.riotanalizer.domain.TrackingLevel.DISCOVERED;
import static com.riotanalizer.domain.TrackingLevel.TRACKED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckCadenceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    private static Duration delay(TrackingLevel level, Duration idle) {
        return CheckCadence.nextDelay(level, true, 0, NOW.minus(idle), false, NOW);
    }

    @Test
    void recentlyActiveTrackedPlayersAreCheckedOften() {
        assertEquals(Duration.ofMinutes(5), delay(TRACKED, Duration.ofMinutes(30)));
        assertEquals(Duration.ofMinutes(15), delay(TRACKED, Duration.ofHours(3)));
        assertEquals(Duration.ofHours(1), delay(TRACKED, Duration.ofDays(1)));
        assertEquals(Duration.ofHours(3), delay(TRACKED, Duration.ofDays(5)));
        assertEquals(Duration.ofHours(12), delay(TRACKED, Duration.ofDays(30)));
    }

    @Test
    void discoveredPlayersAreCheckedMuchLessOften() {
        assertEquals(Duration.ofHours(2), delay(DISCOVERED, Duration.ofHours(3)));
        assertEquals(Duration.ofHours(12), delay(DISCOVERED, Duration.ofDays(3)));
        assertEquals(Duration.ofDays(3), delay(DISCOVERED, Duration.ofDays(30)));
    }

    @Test
    void inGameTrackedPlayerIsCheckedRightAfterTheGameLikelyEnds() {
        assertEquals(CheckCadence.IN_GAME,
                CheckCadence.nextDelay(TRACKED, true, 0, NOW.minus(Duration.ofDays(2)), true, NOW));
    }

    @Test
    void unfinishedBackfillContinuesImmediatelyUnlessItIsStuck() {
        assertEquals(Duration.ZERO, CheckCadence.nextDelay(TRACKED, false, 25, null, false, NOW));
        assertEquals(CheckCadence.TRACKED_ERROR_BACKOFF, CheckCadence.nextDelay(TRACKED, false, 0, null, false, NOW));
        assertEquals(CheckCadence.DISCOVERED_ERROR_BACKOFF,
                CheckCadence.nextDelay(DISCOVERED, false, 0, null, false, NOW));
    }

    @Test
    void noGamesThisSeasonMeansRareChecks() {
        assertEquals(Duration.ofHours(12), CheckCadence.nextDelay(TRACKED, true, 0, null, false, NOW));
        assertEquals(Duration.ofDays(3), CheckCadence.nextDelay(DISCOVERED, true, 0, null, false, NOW));
    }

    @Test
    void profileIsRefreshedOnlyAfterNewGamesOrWhenOld() {
        Instant hourAgo = NOW.minus(Duration.ofHours(1));
        assertTrue(CheckCadence.needsProfileRefresh(TRACKED, null, 0, NOW));
        assertFalse(CheckCadence.needsProfileRefresh(TRACKED, hourAgo, 0, NOW));
        assertTrue(CheckCadence.needsProfileRefresh(TRACKED, hourAgo, 1, NOW));
        assertTrue(CheckCadence.needsProfileRefresh(TRACKED, NOW.minus(Duration.ofHours(7)), 0, NOW));

        assertFalse(CheckCadence.needsProfileRefresh(DISCOVERED, hourAgo, 3, NOW));
        assertTrue(CheckCadence.needsProfileRefresh(DISCOVERED, NOW.minus(Duration.ofDays(2)), 3, NOW));
        assertTrue(CheckCadence.needsProfileRefresh(DISCOVERED, NOW.minus(Duration.ofDays(8)), 0, NOW));
    }

    @Test
    void profileOnlyRefreshesWeekly() {
        java.time.Instant now = java.time.Instant.parse("2026-10-25T12:00:00Z");
        org.junit.jupiter.api.Assertions.assertTrue(CheckCadence.profileOnlyNeedsRefresh(null, now));
        org.junit.jupiter.api.Assertions.assertFalse(
                CheckCadence.profileOnlyNeedsRefresh(now.minus(java.time.Duration.ofDays(2)), now));
        org.junit.jupiter.api.Assertions.assertTrue(
                CheckCadence.profileOnlyNeedsRefresh(now.minus(java.time.Duration.ofDays(7)), now));
        org.junit.jupiter.api.Assertions.assertEquals(now.plus(java.time.Duration.ofDays(5)),
                CheckCadence.nextProfileOnlyCheck(now.minus(java.time.Duration.ofDays(2)), now));
        org.junit.jupiter.api.Assertions.assertEquals(now.plus(java.time.Duration.ofDays(7)),
                CheckCadence.nextProfileOnlyCheck(null, now));
    }
}
