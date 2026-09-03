package com.riotanalizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.MatchEntity;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Gradually backfills a player's <em>current-season</em> match history.
 *
 * <p>Match ids are requested with the Match-V5 {@code startTime} filter set to
 * {@code riot.current-season-start-epoch-ms}, so Riot only ever returns games
 * from the current season onward — the whole of a player's older history is
 * never fetched. Each cycle pulls at most {@code riot.fetch-batch-size} new
 * match details (the "slowly" throttle), walking that season-bounded id list
 * newest-first and skipping anything already stored. Brand-new games sit at the
 * top of the list, so they're always picked up first; the remaining budget then
 * advances a little deeper into the season. Over successive scheduled cycles the
 * whole current season (also bounded by {@code riot.max-history-matches}) is
 * pulled in without ever bursting past the rate limiter. Every match is saved in
 * its own transaction so a long backfill never holds a DB transaction across the
 * network.
 */
@Service
public class FetchService {

    private static final Logger log = LoggerFactory.getLogger(FetchService.class);

    private final RiotApiClient riot;
    private final MatchRepository matches;
    private final RiotProperties props;

    public FetchService(RiotApiClient riot, MatchRepository matches, RiotProperties props) {
        this.riot = riot;
        this.matches = matches;
        this.props = props;
    }

    public int fetchAndStore(Player player) {
        String tag = player.getGameName() + "#" + player.getTagLine();
        int budget = Math.max(1, props.getFetchBatchSize());
        int pageSize = Math.min(100, Math.max(1, props.getMatchPageSize()));
        int maxStart = Math.max(pageSize, props.getMaxHistoryMatches());
        long seasonStartSec = props.getCurrentSeasonStartEpochMs() / 1000L;
        int stored = 0;
        int scanned = 0;
        long startedAt = System.currentTimeMillis();

        log.info("[{}] fetch start — will pull up to {} new match(es) this cycle", tag, budget);

        // Walk the current-season match-id list (newest first; the startTime
        // filter keeps Riot from returning anything older than the season start),
        // fetching details only for matches not yet stored and stopping after
        // `budget` new matches this cycle. Already-stored ids are skipped cheaply,
        // so over successive cycles this advances steadily through the season.
        for (int start = 0; start < maxStart && stored < budget; start += pageSize) {
            List<String> ids = riot.getMatchIds(player.getPuuid(), start, pageSize, seasonStartSec);
            if (ids.isEmpty()) {
                log.debug("[{}] no match ids at offset {} — reached end of the current season", tag, start);
                break;
            }
            scanned += ids.size();
            log.debug("[{}] scanned {} match id(s) at offset {} ({} stored so far this cycle)",
                    tag, ids.size(), start, stored);
            for (String matchId : ids) {
                if (stored >= budget) {
                    break;
                }
                if (matches.existsById(matchId)) {
                    continue;
                }
                try {
                    JsonNode json = riot.getMatch(matchId);
                    MatchEntity match = parse(matchId, json);
                    if (match != null) {
                        matches.save(match);
                        stored++;
                        log.info("[{}] +{} {} ({}/{} this cycle)",
                                tag, matchId, describe(match, player.getPuuid()), stored, budget);
                    }
                } catch (Exception e) {
                    log.warn("[{}] failed to fetch/persist match {}: {}", tag, matchId, e.getMessage());
                }
            }
            // A short page means we've reached the oldest available match.
            if (ids.size() < pageSize) {
                log.debug("[{}] short page ({} < {}) — reached the oldest available match",
                        tag, ids.size(), pageSize);
                break;
            }
        }

        long took = System.currentTimeMillis() - startedAt;
        if (stored > 0) {
            log.info("[{}] fetch done — {} new match(es) stored, {} id(s) scanned in {} ms",
                    tag, stored, scanned, took);
        } else {
            log.info("[{}] fetch done — no new matches ({} id(s) scanned, all already stored) in {} ms",
                    tag, scanned, took);
        }
        return stored;
    }

    /** Short one-line summary of the tracked player's line in a match, for logs. */
    private String describe(MatchEntity match, String puuid) {
        for (Participant p : match.getParticipants()) {
            if (puuid.equals(p.getPuuid())) {
                return p.getChampionName() + " " + (p.isWin() ? "WIN" : "LOSS")
                        + " " + p.getKills() + "/" + p.getDeaths() + "/" + p.getAssists();
            }
        }
        return "";
    }

    private MatchEntity parse(String matchId, JsonNode json) {
        if (json == null || !json.has("info")) {
            return null;
        }
        JsonNode info = json.get("info");
        MatchEntity match = new MatchEntity();
        match.setMatchId(matchId);
        match.setQueueId(intOrNull(info, "queueId"));
        match.setGameCreation(info.path("gameCreation").asLong());
        match.setGameDuration(info.path("gameDuration").asInt());
        match.setGameVersion(info.path("gameVersion").asText(null));
        match.setMapId(intOrNull(info, "mapId"));

        for (JsonNode pn : info.path("participants")) {
            match.addParticipant(parseParticipant(pn));
        }
        return match;
    }

    private Participant parseParticipant(JsonNode pn) {
        Participant p = new Participant();
        p.setPuuid(pn.path("puuid").asText(null));
        p.setRiotIdGameName(text(pn, "riotIdGameName"));
        p.setRiotIdTagLine(text(pn, "riotIdTagline"));
        p.setChampionId(intOrNull(pn, "championId"));
        p.setChampionName(text(pn, "championName"));
        p.setTeamId(intOrNull(pn, "teamId"));
        p.setTeamPosition(text(pn, "teamPosition"));
        p.setWin(pn.path("win").asBoolean(false));
        p.setKills(pn.path("kills").asInt());
        p.setDeaths(pn.path("deaths").asInt());
        p.setAssists(pn.path("assists").asInt());
        int cs = pn.path("totalMinionsKilled").asInt() + pn.path("neutralMinionsKilled").asInt();
        p.setTotalCs(cs);
        p.setVisionScore(pn.path("visionScore").asInt());
        p.setTotalDamageToChampions(pn.path("totalDamageDealtToChampions").asInt());
        p.setGoldEarned(pn.path("goldEarned").asInt());
        p.setItem0(intOrNull(pn, "item0"));
        p.setItem1(intOrNull(pn, "item1"));
        p.setItem2(intOrNull(pn, "item2"));
        p.setItem3(intOrNull(pn, "item3"));
        p.setItem4(intOrNull(pn, "item4"));
        p.setItem5(intOrNull(pn, "item5"));
        p.setItem6(intOrNull(pn, "item6"));
        p.setSummoner1Id(intOrNull(pn, "summoner1Id"));
        p.setSummoner2Id(intOrNull(pn, "summoner2Id"));
        return p;
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
