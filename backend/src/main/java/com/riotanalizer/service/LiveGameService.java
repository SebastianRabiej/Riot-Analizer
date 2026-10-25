package com.riotanalizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.dto.LiveMatchDto;
import com.riotanalizer.dto.LiveParticipantDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live (spectator) game lookups. Results are cached per puuid; the scheduler
 * refreshes the cache for tracked players, and the endpoint serves the cached
 * value (falling back to a fresh fetch when absent).
 *
 * <p>Participants carry their solo-queue rank. Ranks are resolved once per game:
 * while the cached game id is unchanged, the previous ranks are reused, so the
 * scheduler's periodic refresh costs no extra league calls.
 */
@Service
public class LiveGameService {

    private final RiotApiClient riot;
    private final ParticipantRepository participants;
    private final LiveRankResolver ranks;
    private final ConcurrentHashMap<String, LiveMatchDto> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> championNameCache = new ConcurrentHashMap<>();

    public LiveGameService(RiotApiClient riot, ParticipantRepository participants, LiveRankResolver ranks) {
        this.riot = riot;
        this.participants = participants;
        this.ranks = ranks;
    }

    @Transactional(readOnly = true)
    public LiveMatchDto getLive(String puuid) {
        LiveMatchDto cached = cache.get(puuid);
        if (cached != null) {
            return cached;
        }
        return refresh(puuid);
    }

    /** Last known live-game state without calling Riot; empty if never looked up. */
    public Optional<Boolean> cachedInGame(String puuid) {
        return Optional.ofNullable(cache.get(puuid)).map(LiveMatchDto::inGame);
    }

    @Transactional(readOnly = true)
    public LiveMatchDto refresh(String puuid) {
        Optional<JsonNode> game = riot.getActiveGameByPuuid(puuid);
        LiveMatchDto previous = cache.get(puuid);
        LiveMatchDto dto = game.map(g -> toDto(g, previous)).orElseGet(LiveMatchDto::notInGame);
        cache.put(puuid, dto);
        return dto;
    }

    private LiveMatchDto toDto(JsonNode g, LiveMatchDto previous) {
        Long gameId = g.has("gameId") ? g.get("gameId").asLong() : null;
        Map<String, LiveParticipantDto> known = sameGame(previous, gameId) ? byPuuid(previous) : Map.of();
        List<LiveParticipantDto> parts = new ArrayList<>();
        for (JsonNode p : g.path("participants")) {
            Integer championId = intOrNull(p, "championId");
            String puuid = text(p, "puuid");
            LiveParticipantDto prev = puuid == null ? null : known.get(puuid);
            LiveRankResolver.SoloRank solo = prev != null && hasRank(prev)
                    ? new LiveRankResolver.SoloRank(prev.tier(), prev.rank(), prev.leaguePoints())
                    : ranks.resolve(puuid).orElse(null);
            parts.add(new LiveParticipantDto(
                    puuid,
                    text(p, "riotId"),
                    championId == null ? 0 : championId,
                    championName(championId),
                    p.path("teamId").asInt(),
                    p.path("spell1Id").asInt(),
                    p.path("spell2Id").asInt(),
                    solo == null ? null : solo.tier(),
                    solo == null ? null : solo.rank(),
                    solo == null ? null : solo.leaguePoints(),
                    solo == null ? null : (solo.isUnranked() ? Boolean.TRUE : null)
            ));
        }
        return new LiveMatchDto(
                true,
                gameId,
                intOrNull(g, "gameQueueConfigId"),
                text(g, "gameMode"),
                g.has("gameStartTime") ? g.get("gameStartTime").asLong() : null,
                g.has("gameLength") ? (int) g.get("gameLength").asLong() : null,
                intOrNull(g, "mapId"),
                parts
        );
    }

    private static boolean sameGame(LiveMatchDto previous, Long gameId) {
        return previous != null && previous.inGame() && gameId != null
                && Objects.equals(previous.gameId(), gameId) && previous.participants() != null;
    }

    private static Map<String, LiveParticipantDto> byPuuid(LiveMatchDto dto) {
        Map<String, LiveParticipantDto> map = new HashMap<>();
        for (LiveParticipantDto p : dto.participants()) {
            if (p.puuid() != null) {
                map.put(p.puuid(), p);
            }
        }
        return map;
    }

    /** True when the earlier lookup succeeded (ranked or unranked); failed lookups are retried. */
    private static boolean hasRank(LiveParticipantDto p) {
        return p.tier() != null || Boolean.TRUE.equals(p.unranked());
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
