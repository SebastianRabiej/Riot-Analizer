package com.riotanalizer.service;

import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.client.dto.AccountDto;
import com.riotanalizer.domain.Player;
import com.riotanalizer.exception.RiotApiException;
import com.riotanalizer.repository.PlayerRepository;
import com.riotanalizer.repository.PuuidRekeyRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PuuidMigrationRunnerTest {

    private final PlayerRepository players = mock(PlayerRepository.class);
    private final PuuidRekeyRepository rekey = mock(PuuidRekeyRepository.class);
    private final RiotApiClient riot = mock(RiotApiClient.class);
    private final PuuidMigrationGate gate = new PuuidMigrationGate();
    private final PuuidMigrationRunner runner = new PuuidMigrationRunner(players, rekey, riot, gate);

    private static Player player(String puuid, String name, String tag) {
        Player p = new Player();
        p.setPuuid(puuid);
        p.setGameName(name);
        p.setTagLine(tag);
        return p;
    }

    private static RiotApiException decryptError() {
        return new RiotApiException(400, "Riot API error: 400", "Bad Request - Exception decrypting old");
    }

    @Test
    void translatesPlayersWhosePuuidBelongsToAnotherKey() {
        when(players.findAll()).thenReturn(List.of(player("old", "Foo", "EUNE"), player("fine", "Bar", "EUNE")));
        when(riot.getAccountByPuuid("old")).thenThrow(decryptError());
        when(riot.getAccountByPuuid("fine")).thenReturn(new AccountDto("fine", "Bar", "EUNE"));
        when(riot.getAccountByRiotId("Foo", "EUNE")).thenReturn(new AccountDto("new", "Foo", "EUNE"));

        assertEquals(1, runner.migratePlayers());

        verify(rekey).translate("old", "new");
        verify(rekey).updateRiotId("new", "Foo", "EUNE");
        verify(rekey, never()).translate(eq("fine"), anyString());
        verify(rekey).enqueueAllMatches();
    }

    @Test
    void doesNothingWhenAllPuuidsMatchTheKey() {
        when(players.findAll()).thenReturn(List.of(player("fine", "Bar", "EUNE")));
        when(riot.getAccountByPuuid("fine")).thenReturn(new AccountDto("fine", "Bar", "EUNE"));

        assertEquals(0, runner.migratePlayers());

        verify(rekey, never()).translate(anyString(), anyString());
        verify(rekey, never()).enqueueAllMatches();
    }

    @Test
    void otherRiotErrorsAbortButStillOpenTheGate() {
        when(players.findAll()).thenReturn(List.of(player("x", "Foo", "EUNE")));
        when(riot.getAccountByPuuid("x")).thenThrow(new RiotApiException(403, "forbidden", "Forbidden"));

        runner.run(null);

        assertTrue(gate.isOpen());
        verify(rekey, never()).translate(anyString(), anyString());
    }
}
