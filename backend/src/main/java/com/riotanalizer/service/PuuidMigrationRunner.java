package com.riotanalizer.service;

import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.client.dto.AccountDto;
import com.riotanalizer.domain.Player;
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
 * production key is configured. For every stored player this probes the PUUID;
 * a rejected one is re-resolved by Riot ID with the current key and every
 * stored reference is translated. When that happens, all stored matches are
 * also queued so {@link PuuidRekeyWorker} can re-key the other participants.
 *
 * <p>Runs before every other {@link ApplicationRunner} and keeps
 * {@link PuuidMigrationGate} closed until done, so the scheduler waits for it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PuuidMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PuuidMigrationRunner.class);

    private final PlayerRepository players;
    private final PuuidRekeyRepository rekey;
    private final RiotApiClient riot;
    private final PuuidMigrationGate gate;

    public PuuidMigrationRunner(PlayerRepository players, PuuidRekeyRepository rekey,
                                RiotApiClient riot, PuuidMigrationGate gate) {
        this.players = players;
        this.rekey = rekey;
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
        List<Player> all = players.findAll();
        int foreign = 0;
        int translated = 0;
        for (Player p : all) {
            String oldPuuid = p.getPuuid();
            if (!issuedForAnotherKey(p)) {
                continue;
            }
            foreign++;
            String tag = p.getGameName() + "#" + p.getTagLine();
            if (p.getGameName() == null || p.getTagLine() == null) {
                log.warn("[{}] PUUID belongs to another API key and no Riot ID is stored; "
                        + "it will be re-keyed from match data if possible", tag);
                continue;
            }
            try {
                AccountDto account = riot.getAccountByRiotId(p.getGameName(), p.getTagLine());
                rekey.translate(oldPuuid, account.puuid());
                rekey.updateRiotId(account.puuid(), account.gameName(), account.tagLine());
                translated++;
                log.info("[{}] PUUID re-keyed for the current API key", tag);
            } catch (NotFoundException e) {
                log.warn("[{}] PUUID belongs to another API key and the Riot ID no longer resolves "
                        + "(renamed?); it will be re-keyed from match data if possible", tag);
            }
        }
        if (foreign > 0) {
            int queued = rekey.enqueueAllMatches();
            log.warn("API key belongs to a different Riot application than the stored data: "
                            + "re-keyed {}/{} player(s); queued {} match(es) to re-key the remaining participants "
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
            riot.getAccountByPuuid(p.getPuuid());
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
