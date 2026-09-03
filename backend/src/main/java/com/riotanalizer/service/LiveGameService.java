package com.riotanalizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.dto.LiveMatchDto;
import com.riotanalizer.dto.LiveParticipantDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live (spectator) game lookups. Results are cached per puuid; the scheduler
 * refreshes the cache for tracked players, and the endpoint serves the cached
 * value (falling back to a fresh fetch when absent).
 */
@Service
public class LiveGameService {

    private final RiotApiClient riot;
    private final ParticipantRepository participants;
    private final ConcurrentHashMap<String, LiveMatchDto> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> championNameCache = new ConcurrentHashMap<>();

    public LiveGameService(RiotApiClient riot, ParticipantRepository participants) {
        this.riot = riot;
        this.participants = participants;
    }

    @Transactional(readOnly = true)
    public LiveMatchDto getLive(String puuid) {
        LiveMatchDto cached = cache.get(puuid);
        if (cached != null) {
            return cached;
        }
        return refresh(puuid);
    }

    @Transactional(readOnly = true)
    public LiveMatchDto refresh(String puuid) {
        Optional<JsonNode> game = riot.getActiveGameByPuuid(puuid);
        LiveMatchDto dto = game.map(this::toDto).orElseGet(LiveMatchDto::notInGame);
        cache.put(puuid, dto);
        return dto;
    }

    private LiveMatchDto toDto(JsonNode g) {
        List<LiveParticipantDto> parts = new ArrayList<>();
        for (JsonNode p : g.path("participants")) {
            Integer championId = intOrNull(p, "championId");
            parts.add(new LiveParticipantDto(
                    text(p, "puuid"),
                    text(p, "riotId"),
                    championId == null ? 0 : championId,
                    championName(championId),
                    p.path("teamId").asInt(),
                    p.path("spell1Id").asInt(),
                    p.path("spell2Id").asInt(),
                    null,
                    null,
                    null
            ));
        }
        return new LiveMatchDto(
                true,
                g.has("gameId") ? g.get("gameId").asLong() : null,
                intOrNull(g, "gameQueueConfigId"),
                text(g, "gameMode"),
                g.has("gameStartTime") ? g.get("gameStartTime").asLong() : null,
                g.has("gameLength") ? (int) g.get("gameLength").asLong() : null,
                intOrNull(g, "mapId"),
                parts
        );
    }

    private String championName(Integer championId) {
        if (championId == null) {
            return null;
        }
        return championNameCache.computeIfAbsent(championId,
                id -> participants.findFirstByChampionId(id)
                        .map(com.riotanalizer.domain.Participant::getChampionName)
                        .orElse(null));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asInt();
    }
}
