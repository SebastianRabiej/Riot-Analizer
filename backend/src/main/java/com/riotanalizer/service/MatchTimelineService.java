package com.riotanalizer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.domain.MatchEntity;
import com.riotanalizer.domain.MatchTimelineCache;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.dto.TimelineAnalysisDto;
import com.riotanalizer.dto.TimelineEventDto;
import com.riotanalizer.dto.TimelineFrameDto;
import com.riotanalizer.dto.TimelineParticipantDto;
import com.riotanalizer.dto.TimelineParticipantFrameDto;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.repository.MatchRepository;
import com.riotanalizer.repository.MatchTimelineCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produces a compact, cached distillation of a Match-V5 timeline. The heavy
 * per-player analysis (laning diffs, kill maps, death review) is derived from
 * this shape on the client; here we only reshape and cache.
 */
@Service
public class MatchTimelineService {

    private static final Logger log = LoggerFactory.getLogger(MatchTimelineService.class);

    /** Bump when the distilled timeline shape changes so old cache rows are refetched. */
    private static final int SCHEMA_VERSION = 3;

    /** Event types we keep; everything else (item purchases, skill levels…) is dropped. */
    private static final Set<String> KEEP = Set.of(
            "CHAMPION_KILL", "ELITE_MONSTER_KILL", "BUILDING_KILL",
            "TURRET_PLATE_DESTROYED", "WARD_PLACED", "WARD_KILL", "LEVEL_UP",
            "ITEM_PURCHASED", "ITEM_SOLD", "ITEM_UNDO");

    private final RiotApiClient riot;
    private final MatchRepository matches;
    private final MatchTimelineCacheRepository cache;
    private final ObjectMapper mapper;

    public MatchTimelineService(RiotApiClient riot, MatchRepository matches,
                                MatchTimelineCacheRepository cache, ObjectMapper mapper) {
        this.riot = riot;
        this.matches = matches;
        this.cache = cache;
        this.mapper = mapper;
    }

    public TimelineAnalysisDto analysis(String matchId) {
        MatchTimelineCache hit = cache.findById(matchId).orElse(null);
        if (hit != null && hit.getJson() != null
                && hit.getSchemaVersion() != null && hit.getSchemaVersion() == SCHEMA_VERSION) {
            try {
                return mapper.readValue(hit.getJson(), TimelineAnalysisDto.class);
            } catch (JsonProcessingException e) {
                log.warn("Corrupt timeline cache for {}, refetching: {}", matchId, e.getMessage());
            }
        } else if (hit != null) {
            log.info("Timeline cache for {} is stale (v{} != v{}); refetching", matchId,
                    hit.getSchemaVersion(), SCHEMA_VERSION);
        }

        MatchEntity m = matches.findWithParticipants(matchId)
                .orElseThrow(() -> new NotFoundException("Match not found: " + matchId));
        Map<String, Participant> byPuuid = new HashMap<>();
        for (Participant p : m.getParticipants()) {
            byPuuid.put(p.getPuuid(), p);
        }

        JsonNode tl = riot.getMatchTimeline(matchId);
        TimelineAnalysisDto dto = distill(matchId, tl, byPuuid, m.getQueueId(), m.getMapId());

        try {
            cache.save(new MatchTimelineCache(matchId, mapper.writeValueAsString(dto), SCHEMA_VERSION, Instant.now()));
        } catch (JsonProcessingException e) {
            log.warn("Could not cache timeline analysis for {}: {}", matchId, e.getMessage());
        }
        return dto;
    }

    private TimelineAnalysisDto distill(String matchId, JsonNode tl, Map<String, Participant> byPuuid,
                                        Integer queueId, Integer mapId) {
        JsonNode info = tl.path("info");
        long frameInterval = info.path("frameInterval").asLong(60000L);

        List<TimelineParticipantDto> participants = new ArrayList<>();
        JsonNode tps = info.get("participants");
        if (tps != null && tps.isArray()) {
            for (JsonNode tp : tps) {
                int pid = tp.path("participantId").asInt();
                String puuid = tp.path("puuid").asText(null);
                Participant mp = puuid == null ? null : byPuuid.get(puuid);
                int teamId = mp != null && mp.getTeamId() != null ? mp.getTeamId() : (pid <= 5 ? 100 : 200);
                String champ = mp != null ? mp.getChampionName() : null;
                String pos = mp != null ? mp.getTeamPosition() : null;
                participants.add(new TimelineParticipantDto(pid, puuid, teamId, champ, pos));
            }
        }

        List<TimelineFrameDto> frames = new ArrayList<>();
        List<TimelineEventDto> events = new ArrayList<>();
        JsonNode frameArr = info.get("frames");
        if (frameArr != null && frameArr.isArray()) {
            for (JsonNode frame : frameArr) {
                long ts = frame.path("timestamp").asLong();

                List<TimelineParticipantFrameDto> pfs = new ArrayList<>();
                JsonNode pf = frame.get("participantFrames");
                if (pf != null) {
                    for (int pid = 1; pid <= 10; pid++) {
                        JsonNode p = pf.get(String.valueOf(pid));
                        if (p == null) {
                            continue;
                        }
                        int cs = p.path("minionsKilled").asInt(0) + p.path("jungleMinionsKilled").asInt(0);
                        JsonNode posNode = p.get("position");
                        int x = posNode == null ? 0 : posNode.path("x").asInt(0);
                        int y = posNode == null ? 0 : posNode.path("y").asInt(0);
                        int dmg = p.path("damageStats").path("totalDamageDoneToChampions").asInt(0);
                        pfs.add(new TimelineParticipantFrameDto(
                                pid,
                                p.path("totalGold").asInt(0),
                                p.path("xp").asInt(0),
                                cs,
                                p.path("level").asInt(0),
                                x, y, dmg,
                                p.path("currentGold").asInt(0)));
                    }
                }
                frames.add(new TimelineFrameDto(ts, pfs));

                JsonNode evs = frame.get("events");
                if (evs != null && evs.isArray()) {
                    for (JsonNode ev : evs) {
                        String type = ev.path("type").asText("");
                        if (KEEP.contains(type)) {
                            events.add(toEvent(type, ev));
                        }
                    }
                }
            }
        }

        return new TimelineAnalysisDto(matchId, frameInterval, queueId, mapId, participants, frames, events);
    }

    private TimelineEventDto toEvent(String type, JsonNode ev) {
        long ts = ev.path("timestamp").asLong();
        JsonNode pos = ev.get("position");
        Integer x = pos == null ? null : optInt(pos, "x");
        Integer y = pos == null ? null : optInt(pos, "y");

        Integer participantId = null;
        Integer killerId = optInt(ev, "killerId");
        Integer victimId = optInt(ev, "victimId");
        Integer teamId = null;
        String subType = null;
        String lane = optText(ev, "laneType");
        Integer bounty = optInt(ev, "bounty");
        Integer shutdown = optInt(ev, "shutdownBounty");
        String wardType = optText(ev, "wardType");
        Integer level = optInt(ev, "level");
        Integer itemId = optInt(ev, "itemId");

        List<Integer> assists = new ArrayList<>();
        JsonNode a = ev.get("assistingParticipantIds");
        if (a != null && a.isArray()) {
            for (JsonNode n : a) {
                assists.add(n.asInt());
            }
        }

        switch (type) {
            case "ELITE_MONSTER_KILL" -> {
                teamId = optInt(ev, "killerTeamId");
                String mt = optText(ev, "monsterType");
                String ms = optText(ev, "monsterSubType");
                subType = ms != null ? ms : mt;
            }
            case "BUILDING_KILL" -> {
                teamId = optInt(ev, "teamId");
                String tt = optText(ev, "towerType");
                subType = tt != null ? tt : optText(ev, "buildingType");
            }
            case "TURRET_PLATE_DESTROYED" -> teamId = optInt(ev, "teamId");
            case "WARD_PLACED" -> participantId = optInt(ev, "creatorId");
            case "WARD_KILL" -> participantId = optInt(ev, "killerId");
            case "LEVEL_UP" -> participantId = optInt(ev, "participantId");
            case "ITEM_PURCHASED", "ITEM_SOLD" -> participantId = optInt(ev, "participantId");
            case "ITEM_UNDO" -> {
                participantId = optInt(ev, "participantId");
                itemId = optInt(ev, "beforeId");
            }
            default -> {
                // CHAMPION_KILL: killer/victim/assists already captured above.
            }
        }

        return new TimelineEventDto(ts, type, participantId, killerId, victimId,
                assists.isEmpty() ? null : assists, teamId, subType, lane, x, y,
                bounty, shutdown, wardType, level, itemId);
    }

    private static Integer optInt(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asInt();
    }

    private static String optText(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
