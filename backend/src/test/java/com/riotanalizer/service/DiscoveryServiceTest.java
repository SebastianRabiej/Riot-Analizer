package com.riotanalizer.service;

import com.riotanalizer.domain.MatchEntity;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import com.riotanalizer.repository.PlayerRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoveryServiceTest {

    private final PlayerRepository players = mock(PlayerRepository.class);

    private static MatchEntity match(String... puuids) {
        MatchEntity m = new MatchEntity();
        m.setMatchId("EUN1_1");
        for (String puuid : puuids) {
            Participant p = new Participant();
            p.setPuuid(puuid);
            p.setRiotIdGameName(puuid.toUpperCase());
            p.setRiotIdTagLine("EUNE");
            m.addParticipant(p);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Player> saved() {
        org.mockito.ArgumentCaptor<Iterable<Player>> captor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(players).saveAll(captor.capture());
        return ((List<Player>) captor.getValue()).stream()
                .collect(Collectors.toMap(Player::getPuuid, Function.identity()));
    }

    @Test
    void otherParticipantsOfATrackedPlayersMatchBecomeDiscovered() {
        Player me = DiscoveryPolicyTest.player(TrackingLevel.TRACKED, 0);
        me.setPuuid("me");
        Player searched = DiscoveryPolicyTest.player(TrackingLevel.NONE, null);
        searched.setPuuid("searched");
        Player friend = DiscoveryPolicyTest.player(TrackingLevel.TRACKED, 0);
        friend.setPuuid("friend");
        when(players.findAllById(any())).thenReturn(List.of(searched, friend));
        DiscoveryService service = new DiscoveryService(players, new DiscoveryPolicy(1, 0));

        int changed = service.onMatchStored(match("me", "new", "searched", "friend"), me);

        assertEquals(2, changed);
        Map<String, Player> saved = saved();
        assertEquals(TrackingLevel.DISCOVERED, saved.get("new").getTrackingLevel());
        assertEquals(1, saved.get("new").getDiscoveryDepth());
        assertEquals("NEW", saved.get("new").getGameName());
        assertEquals(TrackingLevel.DISCOVERED, saved.get("searched").getTrackingLevel());
        assertEquals(TrackingLevel.TRACKED, friend.getTrackingLevel());
    }

    @Test
    void discoveredPlayersDoNotSnowballAtDepthOne() {
        Player source = DiscoveryPolicyTest.player(TrackingLevel.DISCOVERED, 1);
        DiscoveryService service = new DiscoveryService(players, new DiscoveryPolicy(1, 0));

        assertEquals(0, service.onMatchStored(match(source.getPuuid(), "x"), source));
        verify(players, never()).saveAll(any());
    }

    @Test
    void unboundedDiscoverySpreadsOneHopFurther() {
        Player source = DiscoveryPolicyTest.player(TrackingLevel.DISCOVERED, 2);
        when(players.findAllById(any())).thenReturn(List.of());
        DiscoveryService service = new DiscoveryService(players, new DiscoveryPolicy(DiscoveryPolicy.UNBOUNDED, 0));

        service.onMatchStored(match(source.getPuuid(), "x"), source);

        assertEquals(3, saved().get("x").getDiscoveryDepth());
    }

    @Test
    void capStopsNewDiscoveries() {
        Player me = DiscoveryPolicyTest.player(TrackingLevel.TRACKED, 0);
        when(players.findAllById(any())).thenReturn(List.of());
        when(players.countByTrackingLevel(TrackingLevel.DISCOVERED)).thenReturn(9L);
        DiscoveryService service = new DiscoveryService(players, new DiscoveryPolicy(1, 10));

        assertEquals(1, service.onMatchStored(match(me.getPuuid(), "a", "b", "c"), me));
    }
}
