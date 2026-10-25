package com.riotanalizer.service;

import com.riotanalizer.client.PuuidKeyMismatchEvent;
import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.client.dto.AccountDto;
import com.riotanalizer.domain.Player;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.exception.RiotApiException;
import com.riotanalizer.repository.PlayerRepository;
import com.riotanalizer.repository.PuuidRekeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Handles an API key that moved to another Riot application.
 *
 * <p>Riot encrypts PUUIDs per application, so PUUIDs stored under the old key
 * are rejected with 400 "Exception decrypting". Nothing is checked up front —
 * the key is assumed correct. The first such error anywhere
 * ({@link PuuidKeyMismatchEvent}) flags every player "to be checked";
 * {@link PuuidRekeyWorker} then {@link #recheck re-checks} them one by one.
 * The player whose call failed is re-keyed immediately by {@link PlayerService}.
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

    @EventListener
    public void onKeyMismatch(PuuidKeyMismatchEvent event) {
        try {
            int flagged = rekey.markAllPlayersForCheck();
            if (flagged > 0) {
                log.warn("Riot rejected a stored PUUID ({}): the API key belongs to a different Riot application "
                        + "than the stored data. Flagged {} player(s) to be re-checked in the background.",
                        event.riotMessage(), flagged);
            }
        } catch (RuntimeException e) {
            log.warn("Could not flag players for a PUUID re-check: {}", e.getMessage());
        }
    }

    /** Re-check one player now; returns the reloaded player under its current PUUID. */
    public Optional<Player> rekey(Player player) {
        return recheck(player.getPuuid(), player.getGameName(), player.getTagLine())
                .flatMap(players::findById);
    }

    /**
     * Resolve the player by Riot ID with the current key (one call) and, if the
     * PUUID differs, translate every stored reference and queue the player's
     * matches for the participant re-key. Clears the "to be checked" flag.
     *
     * @return the PUUID valid for the current key, or empty if it can't be determined
     * @throws RiotApiException on transient Riot errors — the flag stays set and is retried
     */
    public Optional<String> recheck(String puuid, String gameName, String tagLine) {
        String tag = gameName + "#" + tagLine;
        if (gameName == null || tagLine == null) {
            return unresolvable(puuid, tag);
        }
        AccountDto account;
        try {
            account = riot.getAccountByRiotId(gameName, tagLine);
        } catch (NotFoundException e) {
            return unresolvable(puuid, tag);
        }
        String current = account.puuid();
        if (!current.equals(puuid)) {
            rekey.translate(puuid, current);
            rekey.updateRiotId(current, account.gameName(), account.tagLine());
            int queued = rekey.enqueueMatchesOf(current);
            log.warn("[{}] PUUID re-keyed for the current API key; queued {} match(es) "
                    + "to re-key the other participants", tag, queued);
        }
        rekey.clearCheck(current);
        return Optional.of(current);
    }

    /**
     * Riot ID unknown or no longer resolving (renamed). Probe the stored PUUID:
     * still valid → done; rejected → leave it to the match re-key, which maps
     * participants by match data and also fixes the player row.
     */
    private Optional<String> unresolvable(String puuid, String tag) {
        try {
            riot.getSummonerByPuuid(puuid);
            rekey.clearCheck(puuid);
            return Optional.of(puuid);
        } catch (NotFoundException e) {
            rekey.clearCheck(puuid);
            return Optional.empty();
        } catch (RiotApiException e) {
            if (!e.isDecryptionError()) {
                throw e;
            }
            int queued = rekey.enqueueMatchesOf(puuid);
            rekey.clearCheck(puuid);
            log.warn("[{}] PUUID belongs to another API key and the Riot ID no longer resolves (renamed?); "
                    + "queued {} match(es) to re-key it from match data", tag, queued);
            return Optional.empty();
        }
    }
}
