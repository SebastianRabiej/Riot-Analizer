package com.riotanalizer.service;

import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.client.dto.AccountDto;
import com.riotanalizer.domain.Player;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.repository.PlayerRepository;
import com.riotanalizer.repository.PuuidRekeyRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerRekeyServiceTest {

    private final RiotApiClient riot = mock(RiotApiClient.class);
    private final PuuidRekeyRepository rekey = mock(PuuidRekeyRepository.class);
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final PlayerRekeyService service = new PlayerRekeyService(riot, rekey, players);

    @Test
    void translatesToThePuuidResolvedWithTheCurrentKey() {
        Player rekeyed = PuuidMigrationRunnerTest.player("new", "Foo", "EUNE");
        when(riot.getAccountByRiotId("Foo", "EUNE")).thenReturn(new AccountDto("new", "Foo", "EUNE"));
        when(players.findById("new")).thenReturn(Optional.of(rekeyed));

        Optional<Player> result = service.rekey(PuuidMigrationRunnerTest.player("old", "Foo", "EUNE"));

        assertEquals("new", result.orElseThrow().getPuuid());
        verify(rekey).translate("old", "new");
        verify(rekey).updateRiotId("new", "Foo", "EUNE");
        verify(rekey).enqueueMatchesOf("new");
    }

    @Test
    void renamedPlayerIsLeftForTheMatchRekey() {
        when(riot.getAccountByRiotId("Foo", "EUNE")).thenThrow(new NotFoundException("nope"));

        assertTrue(service.rekey(PuuidMigrationRunnerTest.player("old", "Foo", "EUNE")).isEmpty());
        verify(rekey, never()).translate(anyString(), anyString());
    }
}
