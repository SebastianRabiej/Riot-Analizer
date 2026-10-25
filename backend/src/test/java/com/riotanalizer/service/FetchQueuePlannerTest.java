package com.riotanalizer.service;

import com.riotanalizer.domain.TrackingLevel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.riotanalizer.domain.TrackingLevel.DISCOVERED;
import static com.riotanalizer.domain.TrackingLevel.TRACKED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FetchQueuePlannerTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    private static final int BATCH = 25;

    private static FetchQueuePlanner.State synced(TrackingLevel level, Duration idle, Duration profileAge,
                                                  Boolean inGame) {
        return new FetchQueuePlanner.State(level, true, 0, NOW.minus(idle), NOW.minus(profileAge), inGame,
                NOW.minusSeconds(30));
    }

    @Test
    void syncedTrackedPlayerGetsHeadCheckPlusLiveGame() {
        FetchQueuePlanner.Plan plan = FetchQueuePlanner.plan(
                synced(TRACKED, Duration.ofHours(3), Duration.ofHours(1), false), BATCH, NOW);
        assertEquals(FetchQueuePlanner.CheckType.HEAD_CHECK, plan.checkType());
        assertEquals(2, plan.minCalls()); // match ids + live game
        assertEquals(1 + BATCH + 2 + 1, plan.maxCalls());
        assertEquals("Last game 3h ago — every 15m", plan.reason());
    }

    @Test
    void staleProfileIsRefreshedForSure() {
        FetchQueuePlanner.Plan plan = FetchQueuePlanner.plan(
                synced(TRACKED, Duration.ofDays(1), Duration.ofHours(7), false), BATCH, NOW);
        assertEquals(4, plan.minCalls());
        assertTrue(plan.steps().stream().anyMatch(s -> s.startsWith("Refresh profile & rank (last 7h ago)")));
    }

    @Test
    void discoveredPlayerHasNoLiveCheckAndSlowCadence() {
        FetchQueuePlanner.Plan plan = FetchQueuePlanner.plan(
                synced(DISCOVERED, Duration.ofDays(3), Duration.ofHours(2), null), BATCH, NOW);
        assertEquals(1, plan.minCalls());
        assertEquals(1 + BATCH, plan.maxCalls()); // profile only 1d+ after the last refresh
        assertEquals("Last game 3d ago — every 12h", plan.reason());
    }

    @Test
    void inGameTrackedPlayerIsExplained() {
        FetchQueuePlanner.Plan plan = FetchQueuePlanner.plan(
                synced(TRACKED, Duration.ofMinutes(40), Duration.ofMinutes(40), true), BATCH, NOW);
        assertEquals("In a live game — rechecked every 4m", plan.reason());
    }

    @Test
    void newPlayerGetsFirstFetch() {
        FetchQueuePlanner.State s = new FetchQueuePlanner.State(DISCOVERED, false, 0, null, null, null, null);
        FetchQueuePlanner.Plan plan = FetchQueuePlanner.plan(s, BATCH, NOW);
        assertEquals(FetchQueuePlanner.CheckType.BACKFILL, plan.checkType());
        assertEquals("First fetch: read this season's match list", plan.steps().get(0));
        assertEquals(3, plan.minCalls()); // match ids + summoner + league
        assertEquals("Just discovered — never fetched", plan.reason());
    }

    @Test
    void backfillResumesFromOffset() {
        FetchQueuePlanner.State s = new FetchQueuePlanner.State(TRACKED, false, 300, NOW.minus(Duration.ofHours(2)),
                NOW.minus(Duration.ofMinutes(5)), false, NOW);
        FetchQueuePlanner.Plan plan = FetchQueuePlanner.plan(s, BATCH, NOW);
        assertEquals("Continue season backfill from match #300", plan.steps().get(0));
        assertEquals("Season not fully stored — re-queued right after each batch", plan.reason());
    }
}
