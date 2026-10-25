package com.riotanalizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.MatchEntity;
import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import com.riotanalizer.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FetchServiceTest {

    private static final long SEASON_START_MS = 1_767_225_600_000L;
    private static final Instant NEWEST = Instant.ofEpochMilli(SEASON_START_MS + 86_400_000L);

    private final RiotApiClient riot = mock(RiotApiClient.class);
    private final MatchRepository matches = mock(MatchRepository.class);
    private final DiscoveryService discovery = mock(DiscoveryService.class);
    private final RiotProperties props = new RiotProperties();
    private FetchService service;

    @BeforeEach
    void setUp() throws Exception {
        props.setCurrentSeasonStartEpochMs(SEASON_START_MS);
        props.setMatchPageSize(100);
        props.setFetchBatchSize(25);
        props.setMaxHistoryMatches(2000);
        service = new FetchService(riot, matches, props, discovery);
        when(riot.getMatch(anyString())).thenAnswer(inv -> match(inv.getArgument(0)));
        when(matches.save(any(MatchEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Player syncedPlayer() {
        Player p = new Player();
        p.setPuuid("me");
        p.setGameName("Me");
        p.setTagLine("EUNE");
        p.setTrackingLevel(TrackingLevel.TRACKED);
        p.setBackfillComplete(true);
        p.setNewestMatchAt(NEWEST);
        return p;
    }

    private static JsonNode match(String id) throws Exception {
        return new ObjectMapper().readTree("""
                {"info":{"gameCreation":%d,"gameDuration":1800,"queueId":420,"participants":[
                  {"puuid":"me","riotIdGameName":"Me","riotIdTagline":"EUNE","win":true},
                  {"puuid":"other","riotIdGameName":"Other","riotIdTagline":"EUNE","win":false}]}}
                """.formatted(NEWEST.toEpochMilli() + 3_600_000L));
    }

    @Test
    void upToDatePlayerCostsASingleCall() {
        Player p = syncedPlayer();
        when(riot.getMatchIds(eq("me"), eq(0), anyInt(), eq(NEWEST.getEpochSecond()))).thenReturn(List.of("EUN1_1"));
        when(matches.existsById("EUN1_1")).thenReturn(true);

        FetchService.SyncResult result = service.sync(p);

        assertEquals(0, result.stored());
        assertTrue(p.isBackfillComplete());
        verify(riot, times(1)).getMatchIds(anyString(), anyInt(), anyInt(), any());
        verify(riot, never()).getMatch(anyString());
    }

    @Test
    void headCheckFetchesOnlyTheNewMatchesAndDiscoversParticipants() {
        Player p = syncedPlayer();
        when(riot.getMatchIds(eq("me"), eq(0), anyInt(), eq(NEWEST.getEpochSecond())))
                .thenReturn(List.of("EUN1_2", "EUN1_1"));
        when(matches.existsById("EUN1_1")).thenReturn(true);
        when(matches.existsById("EUN1_2")).thenReturn(false);
        Instant newer = NEWEST.plusSeconds(3600);
        when(matches.findGameCreation("EUN1_2")).thenReturn(Optional.of(newer.toEpochMilli()));

        FetchService.SyncResult result = service.sync(p);

        assertEquals(1, result.stored());
        assertTrue(p.isBackfillComplete());
        assertEquals(newer, p.getNewestMatchAt());
        verify(riot).getMatch("EUN1_2");
        verify(riot, never()).getMatch("EUN1_1");
        verify(discovery).onMatchStored(any(MatchEntity.class), eq(p));
    }

    @Test
    void tooManyNewMatchesFallBackToBackfill() {
        props.setFetchBatchSize(1);
        Player p = syncedPlayer();
        when(riot.getMatchIds(eq("me"), eq(0), anyInt(), eq(NEWEST.getEpochSecond())))
                .thenReturn(List.of("EUN1_3", "EUN1_2"));
        when(riot.getMatchIds(eq("me"), eq(0), anyInt(), eq(SEASON_START_MS / 1000L)))
                .thenReturn(List.of("EUN1_3", "EUN1_2", "EUN1_1"));

        FetchService.SyncResult result = service.sync(p);

        assertEquals(1, result.stored());
        assertFalse(p.isBackfillComplete(), "one batch stored, the rest comes next check");
        verify(riot, times(1)).getMatch(anyString());
    }

    @Test
    void backfillMarksSeasonCompleteAtTheEndOfTheList() {
        Player p = syncedPlayer();
        p.setBackfillComplete(false);
        p.setNewestMatchAt(null);
        when(riot.getMatchIds(eq("me"), eq(0), anyInt(), eq(SEASON_START_MS / 1000L)))
                .thenReturn(List.of("EUN1_2", "EUN1_1"));
        when(matches.existsById("EUN1_1")).thenReturn(true);
        when(matches.findGameCreation("EUN1_2")).thenReturn(Optional.of(NEWEST.toEpochMilli()));

        FetchService.SyncResult result = service.sync(p);

        assertEquals(1, result.stored());
        assertTrue(result.backfillComplete());
        assertTrue(p.isBackfillComplete());
        assertEquals(NEWEST, p.getNewestMatchAt());
        verify(riot, times(1)).getMatchIds(anyString(), anyInt(), anyInt(), anyLong());
    }

    @Test
    void failedMatchKeepsBackfillOpenSoItIsRetried() {
        Player p = syncedPlayer();
        p.setBackfillComplete(false);
        when(riot.getMatchIds(eq("me"), eq(0), anyInt(), eq(SEASON_START_MS / 1000L)))
                .thenReturn(List.of("EUN1_1"));
        when(riot.getMatch("EUN1_1")).thenThrow(new RuntimeException("boom"));

        FetchService.SyncResult result = service.sync(p);

        assertEquals(0, result.stored());
        assertFalse(p.isBackfillComplete());
    }

    @Test
    void backfillResumesWhereTheLastBatchStoppedInsteadOfRescanning() {
        props.setMatchPageSize(2);
        props.setFetchBatchSize(1);
        Player p = syncedPlayer();
        p.setBackfillComplete(false);
        p.setBackfillOffset(6);
        long season = SEASON_START_MS / 1000L;
        when(riot.getMatchIds("me", 0, 2, season)).thenReturn(List.of("A", "B"));
        when(riot.getMatchIds("me", 4, 2, season)).thenReturn(List.of("C", "D"));
        when(matches.existsById("A")).thenReturn(true);
        when(matches.existsById("B")).thenReturn(true);

        FetchService.SyncResult result = service.sync(p);

        assertEquals(1, result.stored());
        assertFalse(p.isBackfillComplete());
        assertEquals(4, p.getBackfillOffset(), "next time resume on the page that still has D");
        verify(riot, never()).getMatchIds("me", 2, 2, season); // skipped: finished in earlier cycles
        verify(riot).getMatch("C");
        verify(riot, never()).getMatch("D");
    }

    @Test
    void completedBackfillResetsTheCursor() {
        Player p = syncedPlayer();
        p.setBackfillComplete(false);
        p.setBackfillOffset(300);
        when(riot.getMatchIds(eq("me"), eq(0), anyInt(), eq(SEASON_START_MS / 1000L))).thenReturn(List.of("A"));
        when(matches.existsById("A")).thenReturn(true);

        service.sync(p);

        assertTrue(p.isBackfillComplete());
        assertEquals(0, p.getBackfillOffset());
    }
}
