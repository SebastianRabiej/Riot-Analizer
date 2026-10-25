package com.riotanalizer.service;

import com.riotanalizer.client.PuuidKeyMismatchEvent;
import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.client.dto.AccountDto;
import com.riotanalizer.client.dto.SummonerDto;
import com.riotanalizer.domain.Player;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.exception.RiotApiException;
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
    void decryptionErrorFlagsEveryPlayer() {
        service.onKeyMismatch(new PuuidKeyMismatchEvent("Bad Request - Exception decrypting x"));

        verify(rekey).markAllPlayersForCheck();
    }

    @Test
    void translatesToThePuuidResolvedWithTheCurrentKey() {
        when(riot.getAccountByRiotId("Foo", "EUNE")).thenReturn(new AccountDto("new", "Foo", "EUNE"));
        when(players.findById("new")).thenReturn(Optional.of(player("new", "Foo", "EUNE")));

        Optional<Player> result = service.rekey(player("old", "Foo", "EUNE"));

        assertEquals("new", result.orElseThrow().getPuuid());
        verify(rekey).translate("old", "new");
        verify(rekey).updateRiotId("new", "Foo", "EUNE");
        verify(rekey).enqueueMatchesOf("new");
        verify(rekey).clearCheck("new");
    }

    @Test
    void unchangedPuuidOnlyClearsTheFlag() {
        when(riot.getAccountByRiotId("Foo", "EUNE")).thenReturn(new AccountDto("same", "Foo", "EUNE"));

        assertEquals(Optional.of("same"), service.recheck("same", "Foo", "EUNE"));

        verify(rekey, never()).translate(anyString(), anyString());
        verify(rekey, never()).enqueueMatchesOf(anyString());
        verify(rekey).clearCheck("same");
    }

    @Test
    void renamedPlayerWithValidPuuidIsKept() {
        when(riot.getAccountByRiotId("Foo", "EUNE")).thenThrow(new NotFoundException("nope"));
        when(riot.getSummonerByPuuid("p")).thenReturn(new SummonerDto("sid", "p", 1, 1L));

        assertEquals(Optional.of("p"), service.recheck("p", "Foo", "EUNE"));

        verify(rekey, never()).enqueueMatchesOf(anyString());
        verify(rekey).clearCheck("p");
    }

    @Test
    void renamedPlayerWithForeignPuuidIsLeftForTheMatchRekey() {
        when(riot.getAccountByRiotId("Foo", "EUNE")).thenThrow(new NotFoundException("nope"));
        when(riot.getSummonerByPuuid("old")).thenThrow(decryptError());

        assertTrue(service.recheck("old", "Foo", "EUNE").isEmpty());

        verify(rekey, never()).translate(anyString(), anyString());
        verify(rekey).enqueueMatchesOf("old");
        verify(rekey).clearCheck("old");
    }
}
