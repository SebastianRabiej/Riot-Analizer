package com.riotanalizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.dto.LiveMatchDto;
import com.riotanalizer.dto.LiveParticipantDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveGameServiceTest {

    private final RiotApiClient riot = mock(RiotApiClient.class);
    private final ParticipantRepository participants = mock(ParticipantRepository.class);
    private final LiveRankResolver ranks = mock(LiveRankResolver.class);
    private final LiveGameService service = new LiveGameService(riot, participants, ranks);

    private static JsonNode game(long gameId) throws Exception {
        return new ObjectMapper().readTree("""
                {"gameId": %d, "gameQueueConfigId": 420, "gameMode": "CLASSIC",
                 "participants": [
                   {"puuid": "a", "riotId": "A#EUNE", "championId": 1, "teamId": 100},
                   {"puuid": "b", "riotId": "B#EUNE", "championId": 2, "teamId": 200},
                   {"puuid": "c", "riotId": "C#EUNE", "championId": 3, "teamId": 200}
                 ]}
                """.formatted(gameId));
    }

    private static LiveParticipantDto part(LiveMatchDto dto, String puuid) {
        return dto.participants().stream().filter(p -> puuid.equals(p.puuid())).findFirst().orElseThrow();
    }

    @Test
    void fillsSoloRankPerParticipant() throws Exception {
        when(participants.findFirstByChampionId(anyInt())).thenReturn(Optional.empty());
        when(riot.getActiveGameByPuuid("me")).thenReturn(Optional.of(game(1)));
        when(ranks.resolve("a")).thenReturn(Optional.of(new LiveRankResolver.SoloRank("GOLD", "II", 45)));
        when(ranks.resolve("b")).thenReturn(Optional.of(LiveRankResolver.SoloRank.UNRANKED));
        when(ranks.resolve("c")).thenReturn(Optional.empty());

        LiveMatchDto dto = service.refresh("me");

        assertEquals("GOLD", part(dto, "a").tier());
        assertEquals("II", part(dto, "a").rank());
        assertEquals(45, part(dto, "a").leaguePoints());
        assertNull(part(dto, "a").unranked());
        assertNull(part(dto, "b").tier());
        assertTrue(part(dto, "b").unranked());
        assertNull(part(dto, "c").tier());
        assertNull(part(dto, "c").unranked());
    }

    @Test
    void sameGameReusesRanksAndRetriesOnlyFailedLookups() throws Exception {
        when(participants.findFirstByChampionId(anyInt())).thenReturn(Optional.empty());
        when(riot.getActiveGameByPuuid("me")).thenReturn(Optional.of(game(1)));
        when(ranks.resolve("a")).thenReturn(Optional.of(new LiveRankResolver.SoloRank("GOLD", "II", 45)));
        when(ranks.resolve("b")).thenReturn(Optional.of(LiveRankResolver.SoloRank.UNRANKED));
        when(ranks.resolve("c")).thenReturn(Optional.empty());

        service.refresh("me");
        LiveMatchDto again = service.refresh("me");

        verify(ranks, times(1)).resolve("a");
        verify(ranks, times(1)).resolve("b");
        verify(ranks, times(2)).resolve("c");
        assertEquals("GOLD", part(again, "a").tier());
        assertTrue(part(again, "b").unranked());
    }

    @Test
    void newGameResolvesAgain() throws Exception {
        when(participants.findFirstByChampionId(anyInt())).thenReturn(Optional.empty());
        when(ranks.resolve("a")).thenReturn(Optional.of(new LiveRankResolver.SoloRank("GOLD", "II", 45)));
        when(riot.getActiveGameByPuuid("me")).thenReturn(Optional.of(game(1)));
        service.refresh("me");
        when(riot.getActiveGameByPuuid("me")).thenReturn(Optional.of(game(2)));
        service.refresh("me");

        verify(ranks, times(2)).resolve("a");
    }
}
