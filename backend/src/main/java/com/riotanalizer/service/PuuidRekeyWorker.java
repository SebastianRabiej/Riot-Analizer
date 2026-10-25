package com.riotanalizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.exception.RateLimitedException;
import com.riotanalizer.exception.RiotApiException;
import com.riotanalizer.repository.PuuidRekeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Drains {@code puuid_rekey_pending}: re-fetches each queued match with the
 * current API key and translates every participant PUUID that changed (all
 * of that person's rows at once, so later matches are often already done).
 * Players whose Riot ID no longer resolved at startup get re-keyed here too.
 * Resumable across restarts; all calls go through the shared rate limiter.
 */
@Component
public class PuuidRekeyWorker {

    private static final Logger log = LoggerFactory.getLogger(PuuidRekeyWorker.class);

    private final PuuidRekeyRepository rekey;
    private final RiotApiClient riot;
    private final PuuidMigrationGate gate;
    private final int batchSize;

    public PuuidRekeyWorker(PuuidRekeyRepository rekey, RiotApiClient riot, PuuidMigrationGate gate,
                            @Value("${riot.puuid-rekey-batch-size:20}") int batchSize) {
        this.rekey = rekey;
        this.riot = riot;
        this.gate = gate;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${riot.puuid-rekey-interval-ms:5000}",
            initialDelayString = "${riot.puuid-rekey-interval-ms:5000}")
    public void run() {
        if (!gate.awaitOpen()) {
            return;
        }
        List<String> batch = rekey.nextPending(batchSize);
        if (batch.isEmpty()) {
            return;
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
                break; // retry next tick
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
    }

    void rekeyMatch(String matchId, JsonNode match) {
        JsonNode apiParticipants = match == null ? null : match.path("info").path("participants");
        Map<String, String> pairs = ParticipantRemap.pairs(rekey.participantsOf(matchId), apiParticipants);
        pairs.forEach(rekey::translate);
        rekey.markDone(matchId);
    }
}
