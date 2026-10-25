package com.riotanalizer.service;

import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.exception.RiotApiException;
import com.riotanalizer.repository.PlayerRepository;
import com.riotanalizer.repository.PuuidRekeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Startup check for an API-key switch to another Riot application.
 *
 * <p>Riot encrypts PUUIDs per application, so PUUIDs stored while using a
 * development key are rejected (400 "Exception decrypting") once a personal or
 * production key is configured. Every stored player's PUUID is probed with
 * SUMMONER-V4 by-puuid (the first call every refresh makes); a rejected one is
 * re-keyed via {@link PlayerRekeyService}. When that happens all stored
 * matches are queued so {@link PuuidRekeyWorker} re-keys the other participants.
 *
 * <p>Runs before every other {@link ApplicationRunner} and keeps
 * {@link PuuidMigrationGate} closed until done, so the scheduler waits for it.
 * Anything this misses is still healed on the fly by {@link PlayerService}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PuuidMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PuuidMigrationRunner.class);

    private final PlayerRepository players;
    private final PuuidRekeyRepository rekey;
    private final PlayerRekeyService playerRekey;
    private final RiotApiClient riot;
    private final PuuidMigrationGate gate;

    public PuuidMigrationRunner(PlayerRepository players, PuuidRekeyRepository rekey,
                                PlayerRekeyService playerRekey, RiotApiClient riot, PuuidMigrationGate gate) {
        this.players = players;
        this.rekey = rekey;
        this.playerRekey = playerRekey;
        this.riot = riot;
        this.gate = gate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            migratePlayers();
        } catch (RuntimeException e) {
            log.warn("PUUID key check aborted, stored PUUIDs were not verified: {}", e.getMessage());
        } finally {
            gate.open();
        }
    }

    /** Returns the number of players whose PUUID was translated. */
    int migratePlayers() {
        // DISCOVERED players can number in the thousands; probing each would burn
        // the rate limit at startup. Their PUUIDs are healed by the background
        // match re-key (PuuidRekeyWorker translates every participant) or, at the
        // latest, re-keyed on the fly when the scheduler first touches them.
        List<Player> all = players.findByTrackingLevelNot(TrackingLevel.DISCOVERED);
        log.info("PUUID key check: probing {} stored player(s) against the current API key", all.size());
        int foreign = 0;
        int translated = 0;
        for (Player p : all) {
            String tag = p.getGameName() + "#" + p.getTagLine();
            if (!issuedForAnotherKey(p)) {
                log.info("PUUID key check: [{}] OK", tag);
                continue;
            }
            foreign++;
            log.warn("PUUID key check: [{}] rejected by Riot (issued for another API key), re-keying", tag);
            if (playerRekey.rekey(p).isPresent()) {
                translated++;
            }
        }
        if (foreign > 0) {
            int queued = rekey.enqueueAllMatches();
            log.warn("PUUID key check: API key belongs to a different Riot application than the stored data. "
                    + "Re-keyed {}/{} player(s); queued {} more match(es) to re-key the other participants "
                    + "in the background", translated, foreign, queued);
        } else {
            log.info("PUUID key check: all {} stored player(s) match the current API key", all.size());
        }
        return translated;
    }

    /**
     * True if Riot rejects the stored PUUID as encrypted for another application.
     * Other Riot errors (bad key, outage, rate limit) abort the whole check.
     */
    private boolean issuedForAnotherKey(Player p) {
        try {
            riot.getSummonerByPuuid(p.getPuuid());
            return false;
        } catch (NotFoundException e) {
            return false;
        } catch (RiotApiException e) {
            if (e.isDecryptionError()) {
                return true;
            }
            throw e;
        }
    }
}
