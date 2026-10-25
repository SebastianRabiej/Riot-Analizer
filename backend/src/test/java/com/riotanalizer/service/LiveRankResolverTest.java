package com.riotanalizer.service;

import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.client.dto.LeagueEntryDto;
import com.riotanalizer.domain.Player;
import com.riotanalizer.exception.RateLimitedException;
import com.riotanalizer.repository.PlayerRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveRankResolverTest {

    private static final Instant NOW = Instant.parse("2026-10-25T18:00:00Z");

    private final RiotApiClient riot = mock(RiotApiClient.class);
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final LiveRankResolver resolver =
            new LiveRankResolver(riot, players, Clock.fixed(NOW, ZoneOffset.UTC));

    private static LeagueEntryDto entry(String queue, String tier, String rank, int lp) {
        return new LeagueEntryDto(queue, tier, rank, lp, 10, 10);
    }

    @Test
    void picksTheSoloQueueEntry() {
        when(players.findById("p")).thenReturn(Optional.empty());
        when(riot.getLeagueEntriesByPuuid("p")).thenReturn(List.of(
                entry("RANKED_FLEX_SR", "SILVER", "I", 5),
                entry("RANKED_SOLO_5x5", "GOLD", "II", 45)));

        LiveRankResolver.SoloRank rank = resolver.resolve("p").orElseThrow();

        assertEquals(new LiveRankResolver.SoloRank("GOLD", "II", 45), rank);
    }

    @Test
    void flexOnlyIsUnrankedInSolo() {
        when(players.findById("p")).thenReturn(Optional.empty());
        when(riot.getLeagueEntriesByPuuid("p")).thenReturn(List.of(entry("RANKED_FLEX_SR", "SILVER", "I", 5)));

        assertTrue(resolver.resolve("p").orElseThrow().isUnranked());
    }

    @Test
    void cachesLookupsIncludingUnranked() {
        when(players.findById("p")).thenReturn(Optional.empty());
        when(riot.getLeagueEntriesByPuuid("p")).thenReturn(List.of());

        resolver.resolve("p");
        resolver.resolve("p");

        verify(riot, times(1)).getLeagueEntriesByPuuid("p");
    }

    @Test
    void usesARecentlyRefreshedPlayerRowWithoutCallingRiot() {
        Player p = new Player();
        p.setPuuid("p");
        p.setSoloTier("DIAMOND");
        p.setSoloRank("IV");
        p.setSoloLeaguePoints(12);
        p.setLastFetched(NOW.minus(Duration.ofMinutes(5)));
        when(players.findById("p")).thenReturn(Optional.of(p));

        assertEquals(new LiveRankResolver.SoloRank("DIAMOND", "IV", 12), resolver.resolve("p").orElseThrow());
        verify(riot, never()).getLeagueEntriesByPuuid(anyString());
    }

    @Test
    void stalePlayerRowFallsBackToRiot() {
        Player p = new Player();
        p.setPuuid("p");
        p.setSoloTier("IRON");
        p.setLastFetched(NOW.minus(Duration.ofDays(3)));
        when(players.findById("p")).thenReturn(Optional.of(p));
        when(riot.getLeagueEntriesByPuuid("p")).thenReturn(List.of(entry("RANKED_SOLO_5x5", "BRONZE", "I", 70)));

        assertEquals("BRONZE", resolver.resolve("p").orElseThrow().tier());
    }

    @Test
    void failedLookupIsEmptyAndNotCached() {
        when(players.findById("p")).thenReturn(Optional.empty());
        when(riot.getLeagueEntriesByPuuid("p")).thenThrow(new RateLimitedException("429"));

        assertTrue(resolver.resolve("p").isEmpty());
        assertTrue(resolver.resolve("p").isEmpty());
        verify(riot, times(2)).getLeagueEntriesByPuuid("p");
    }

    @Test
    void missingPuuidIsEmpty() {
        assertTrue(resolver.resolve(null).isEmpty());
        verify(riot, never()).getLeagueEntriesByPuuid(anyString());
    }
}
