package com.riotanalizer.service;

import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import com.riotanalizer.ratelimit.RiotRateLimiter;
import com.riotanalizer.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.riotanalizer.domain.TrackingLevel.DISCOVERED;
import static com.riotanalizer.domain.TrackingLevel.TRACKED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Order in which {@link ScheduledFetcher#nextPick} serves due and early work. */
class ScheduledFetcherPickTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    private PlayerRepository players;
    private RiotRateLimiter limiter;
    private ScheduledFetcher fetcher;

    @BeforeEach
    void setUp() {
        players = mock(PlayerRepository.class);
        // 100 calls / 120 s long window, no reserves needed for these tests.
        limiter = new RiotRateLimiter(1000, 1000, 100, 120_000, 0, 0);
        EarlyCheckPolicy policy = new EarlyCheckPolicy(true, Duration.ofMinutes(2), Duration.ofMinutes(30), 0.4, 0.6);
        fetcher = new ScheduledFetcher(players, null, null, null, limiter, policy);
        when(players.findDue(any(), any(), any())).thenReturn(List.of());
        when(players.findEarlyEligible(any(), any(), any())).thenReturn(List.of());
    }

    private static Player player(String puuid, TrackingLevel level) {
        Player p = new Player();
        p.setPuuid(puuid);
        p.setTrackingLevel(level);
        return p;
    }

    private void spend(int calls) {
        for (int i = 0; i < calls; i++) {
            limiter.acquire(com.riotanalizer.ratelimit.FetchPriority.REFRESH);
        }
    }

    @Test
    void dueTrackedBeatsEverything() {
        when(players.findDue(eq(TRACKED), any(), any())).thenReturn(List.of(player("t-due", TRACKED)));
        when(players.findEarlyEligible(eq(TRACKED), any(), any())).thenReturn(List.of(player("t-early", TRACKED)));
        Optional<ScheduledFetcher.Pick> pick = fetcher.nextPick(NOW);
        assertEquals("t-due", pick.orElseThrow().player().getPuuid());
        assertFalse(pick.get().early());
    }

    @Test
    void earlyTrackedGoesBeforeDiscoveredBacklogWhenBudgetIsIdle() {
        when(players.findEarlyEligible(eq(TRACKED), any(), any())).thenReturn(List.of(player("t-early", TRACKED)));
        when(players.findDue(eq(DISCOVERED), any(), any())).thenReturn(List.of(player("d-due", DISCOVERED)));
        ScheduledFetcher.Pick pick = fetcher.nextPick(NOW).orElseThrow();
        assertEquals("t-early", pick.player().getPuuid());
        assertTrue(pick.early());
    }

    @Test
    void busyBudgetSkipsEarlyChecksAndServesDueWork() {
        spend(61); // 39 free < 40 needed for an early tracked check
        when(players.findEarlyEligible(eq(TRACKED), any(), any())).thenReturn(List.of(player("t-early", TRACKED)));
        when(players.findDue(eq(DISCOVERED), any(), any())).thenReturn(List.of(player("d-due", DISCOVERED)));
        ScheduledFetcher.Pick pick = fetcher.nextPick(NOW).orElseThrow();
        assertEquals("d-due", pick.player().getPuuid());
        assertFalse(pick.early());
    }

    @Test
    void earlyDiscoveredNeedsMoreHeadroomThanEarlyTracked() {
        spend(50); // 50 free: enough for tracked (40), not for discovered (60)
        when(players.findEarlyEligible(eq(DISCOVERED), any(), any()))
                .thenReturn(List.of(player("d-early", DISCOVERED)));
        assertTrue(fetcher.nextPick(NOW).isEmpty());
        assertTrue(fetcher.earlyAllowed(TRACKED));
        assertFalse(fetcher.earlyAllowed(DISCOVERED));
    }

    @Test
    void idleBudgetPullsDiscoveredForward() {
        when(players.findEarlyEligible(eq(DISCOVERED), any(), any()))
                .thenReturn(List.of(player("d-early", DISCOVERED)));
        ScheduledFetcher.Pick pick = fetcher.nextPick(NOW).orElseThrow();
        assertEquals("d-early", pick.player().getPuuid());
        assertTrue(pick.early());
    }
}
