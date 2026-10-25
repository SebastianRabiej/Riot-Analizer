package com.riotanalizer.service;

import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.exception.RateLimitedException;
import com.riotanalizer.repository.PuuidRekeyRepository;
import com.riotanalizer.repository.PuuidRekeyRepository.PendingPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PuuidRekeyWorkerTest {

    private final PuuidRekeyRepository rekey = mock(PuuidRekeyRepository.class);
    private final PlayerRekeyService playerRekey = mock(PlayerRekeyService.class);
    private final RiotApiClient riot = mock(RiotApiClient.class);
    private final PuuidRekeyWorker worker = new PuuidRekeyWorker(rekey, playerRekey, riot, 20);

    @Test
    void idleWhenNothingIsFlaggedOrQueued() {
        when(rekey.playersPendingCheck(anyBoolean(), anyInt())).thenReturn(List.of());
        when(rekey.nextPending(anyInt())).thenReturn(List.of());

        worker.run();

        verify(playerRekey, never()).recheck(anyString(), anyString(), anyString());
        verify(riot, never()).getMatch(anyString());
    }

    @Test
    void trackedPlayersAreRecheckedBeforeMatchesAndDiscoveredPlayers() {
        when(rekey.playersPendingCheck(eq(false), anyInt()))
                .thenReturn(List.of(new PendingPlayer("t1", "Foo", "EUNE")));
        when(rekey.countPlayersPendingCheck(false)).thenReturn(5L);

        worker.run();

        verify(playerRekey).recheck("t1", "Foo", "EUNE");
        verify(rekey, never()).nextPending(anyInt());
        verify(rekey, never()).playersPendingCheck(eq(true), anyInt());
    }

    @Test
    void rateLimitPausesTheTick() {
        when(rekey.playersPendingCheck(eq(false), anyInt()))
                .thenReturn(List.of(new PendingPlayer("t1", "Foo", "EUNE"), new PendingPlayer("t2", "Bar", "EUNE")));
        when(playerRekey.recheck("t1", "Foo", "EUNE")).thenThrow(new RateLimitedException("slow down"));

        worker.run();

        verify(playerRekey, never()).recheck("t2", "Bar", "EUNE");
        verify(rekey, never()).nextPending(anyInt());
    }
}
