package com.riotanalizer.service;

import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.client.dto.AccountDto;
import com.riotanalizer.domain.Player;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.repository.PlayerRepository;
import com.riotanalizer.repository.PuuidRekeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Re-keys one stored player whose PUUID Riot rejected as issued for another
 * API key ("Exception decrypting"): resolves the Riot ID with the current key,
 * translates every stored reference and queues the player's matches so
 * {@link PuuidRekeyWorker} re-keys the other participants too.
 */
@Service
public class PlayerRekeyService {

    private static final Logger log = LoggerFactory.getLogger(PlayerRekeyService.class);

    private final RiotApiClient riot;
    private final PuuidRekeyRepository rekey;
    private final PlayerRepository players;

    public PlayerRekeyService(RiotApiClient riot, PuuidRekeyRepository rekey, PlayerRepository players) {
        this.riot = riot;
        this.rekey = rekey;
        this.players = players;
    }

    /**
     * Returns the re-keyed player (reloaded), or empty if the Riot ID is unknown
     * or no longer resolves (renamed) — the background match re-key may still fix it.
     */
    public Optional<Player> rekey(Player player) {
        String tag = player.getGameName() + "#" + player.getTagLine();
        if (player.getGameName() == null || player.getTagLine() == null) {
            log.warn("[{}] PUUID belongs to another API key and no Riot ID is stored; cannot re-key directly", tag);
            return Optional.empty();
        }
        AccountDto account;
        try {
            account = riot.getAccountByRiotId(player.getGameName(), player.getTagLine());
        } catch (NotFoundException e) {
            log.warn("[{}] PUUID belongs to another API key and the Riot ID no longer resolves (renamed?); "
                    + "it will be re-keyed from match data if possible", tag);
            return Optional.empty();
        }
        rekey.translate(player.getPuuid(), account.puuid());
        rekey.updateRiotId(account.puuid(), account.gameName(), account.tagLine());
        int queued = rekey.enqueueMatchesOf(account.puuid());
        log.warn("[{}] PUUID re-keyed for the current API key; queued {} of their match(es) "
                + "to re-key the other participants in the background", tag, queued);
        return players.findById(account.puuid());
    }
}
