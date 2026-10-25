package com.riotanalizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.exception.RateLimitedException;
import com.riotanalizer.exception.RiotApiException;
import com.riotanalizer.ratelimit.FetchContext;
import com.riotanalizer.ratelimit.FetchPriority;
import com.riotanalizer.repository.PuuidRekeyRepository;
import com.riotanalizer.repository.PuuidRekeyRepository.PendingPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Background side of the PUUID re-key; idle (cheap DB queries only) unless the
 * API key changed application. Each tick, in this order:
 * <ol>
 *   <li>flagged TRACKED / NONE players are re-checked by Riot ID
 *       ({@link PlayerRekeyService#recheck}), which also queues their matches;</li>
 *   <li>queued matches ({@code puuid_rekey_pending}) are re-fetched and every
 *       participant PUUID that changed is translated — all of that person's
 *       rows at once, including their player row, so most DISCOVERED players
 *       are fixed here for free;</li>
 *   <li>only then, flagged DISCOVERED players still left are re-checked.</li>
 * </ol>
 * Each step only runs once the previous one is drained.
 * Resumable across restarts; all calls go through the shared rate limiter.
 */
@Component
public class PuuidRekeyWorker {

    private static final Logger log = LoggerFactory.getLogger(PuuidRekeyWorker.class);

    private final PuuidRekeyRepository rekey;
    private final PlayerRekeyService playerRekey;
    private final RiotApiClient riot;
    private final int batchSize;

    public PuuidRekeyWorker(PuuidRekeyRepository rekey, PlayerRekeyService playerRekey, RiotApiClient riot,
                            @Value("${riot.puuid-rekey-batch-size:20}") int batchSize) {
        this.rekey = rekey;
        this.playerRekey = playerRekey;
        this.riot = riot;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${riot.puuid-rekey-interval-ms:5000}",
            initialDelayString = "${riot.puuid-rekey-interval-ms:5000}")
    public void run() {
        // Background maintenance at the matching history priority.
        boolean onlyDiscoveredLeft = FetchContext.call(FetchPriority.TRACKED_HISTORY,
                () -> checkPlayers(false) && rekeyMatches());
        if (onlyDiscoveredLeft) {
            FetchContext.run(FetchPriority.DISCOVERED_HISTORY, () -> checkPlayers(true));
        }
    }

    /** Returns true once no flagged players of that kind are left. */
    boolean checkPlayers(boolean discovered) {
        List<PendingPlayer> batch = rekey.playersPendingCheck(discovered, batchSize);
        if (batch.isEmpty()) {
            return true;
        }
        for (PendingPlayer p : batch) {
            try {
                playerRekey.recheck(p.puuid(), p.gameName(), p.tagLine());
            } catch (RateLimitedException e) {
                return false;
            } catch (RiotApiException e) {
                log.warn("PUUID re-check paused: {}", e.getMessage());
                return false;
            }
        }
        long remaining = rekey.countPlayersPendingCheck(discovered);
        String kind = discovered ? "discovered" : "tracked/known";
        if (remaining == 0) {
            log.info("PUUID re-check: all {} players use the current API key", kind);
        } else {
            log.info("PUUID re-check: {} {} player(s) remaining", remaining, kind);
        }
        return remaining == 0;
    }

    /** Returns true once no matches are queued. */
    boolean rekeyMatches() {
        List<String> batch = rekey.nextPending(batchSize);
        if (batch.isEmpty()) {
            return true;
        }
        int done = 0;
        for (String matchId : batch) {
            try {
                rekeyMatch(matchId, riot.getMatch(matchId));
                done++;
            } catch (NotFoundException e) {
                log.warn("PUUID re-key: match {} no longer available from Riot; leaving it as is", matchId);
                rekey.markDone(matchId);
            } catch (RateLimitedException e) {
                break;
            } catch (RiotApiException e) {
                log.warn("PUUID re-key paused: {}", e.getMessage());
                break;
            }
        }
        long remaining = rekey.countPending();
        if (remaining == 0) {
            log.info("PUUID re-key finished: all stored matches use the current API key");
        } else {
            log.info("PUUID re-key: {} match(es) done this tick, {} remaining", done, remaining);
        }
        return remaining == 0;
    }

    void rekeyMatch(String matchId, JsonNode match) {
        JsonNode apiParticipants = match == null ? null : match.path("info").path("participants");
        Map<String, String> pairs = ParticipantRemap.pairs(rekey.participantsOf(matchId), apiParticipants);
        pairs.forEach(rekey::translate);
        rekey.markDone(matchId);
    }
}
