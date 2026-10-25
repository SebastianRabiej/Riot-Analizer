package com.riotanalizer.service;

import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.client.dto.SummonerDto;
import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import com.riotanalizer.exception.RiotApiException;
import com.riotanalizer.repository.PlayerRepository;
import com.riotanalizer.repository.PuuidRekeyRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PuuidMigrationRunnerTest {

    private final PlayerRepository players = mock(PlayerRepository.class);
    private final PuuidRekeyRepository rekey = mock(PuuidRekeyRepository.class);
    private final PlayerRekeyService playerRekey = mock(PlayerRekeyService.class);
    private final RiotApiClient riot = mock(RiotApiClient.class);
    private final PuuidMigrationGate gate = new PuuidMigrationGate();
    private final PuuidMigrationRunner runner = new PuuidMigrationRunner(players, rekey, playerRekey, riot, gate);

    static Player player(String puuid, String name, String tag) {
        Player p = new Player();
        p.setPuuid(puuid);
        p.setGameName(name);
        p.setTagLine(tag);
        return p;
    }

    static RiotApiException decryptError() {
        return new RiotApiException(400, "Riot API error: 400", "Bad Request - Exception decrypting old");
    }

    @Test
    void rekeysPlayersWhosePuuidBelongsToAnotherKey() {
        Player old = player("old", "Foo", "EUNE");
        Player fine = player("fine", "Bar", "EUNE");
        when(players.findByTrackingLevelNot(TrackingLevel.DISCOVERED)).thenReturn(List.of(old, fine));
        when(riot.getSummonerByPuuid("old")).thenThrow(decryptError());
        when(riot.getSummonerByPuuid("fine")).thenReturn(new SummonerDto("sid", "fine", 1, 1L));
        when(playerRekey.rekey(old)).thenReturn(Optional.of(player("new", "Foo", "EUNE")));

        assertEquals(1, runner.migratePlayers());

        verify(playerRekey).rekey(old);
        verify(playerRekey, never()).rekey(fine);
        verify(rekey).enqueueAllMatches();
    }

    @Test
    void doesNothingWhenAllPuuidsMatchTheKey() {
        when(players.findByTrackingLevelNot(TrackingLevel.DISCOVERED)).thenReturn(List.of(player("fine", "Bar", "EUNE")));
        when(riot.getSummonerByPuuid("fine")).thenReturn(new SummonerDto("sid", "fine", 1, 1L));

        assertEquals(0, runner.migratePlayers());

        verify(playerRekey, never()).rekey(any());
        verify(rekey, never()).enqueueAllMatches();
    }

    @Test
    void otherRiotErrorsAbortButStillOpenTheGate() {
        when(players.findByTrackingLevelNot(TrackingLevel.DISCOVERED)).thenReturn(List.of(player("x", "Foo", "EUNE")));
        when(riot.getSummonerByPuuid("x")).thenThrow(new RiotApiException(403, "forbidden", "Forbidden"));

        runner.run(null);

        assertTrue(gate.isOpen());
        verify(playerRekey, never()).rekey(any());
    }
}
