package com.riotanalizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.MatchEntity;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.exception.RiotApiException;
import com.riotanalizer.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a player's <em>current-season</em> match history in sync, spending as
 * few Riot calls as possible.
 *
 * <p>Two modes, chosen from the player's stored sync state:
 * <ul>
 *   <li><b>Backfill</b> (season not fully stored yet): walk the season-bounded
 *       match-id list newest-first (Match-V5 {@code startTime} =
 *       {@code riot.current-season-start-epoch-ms}), skipping ids already in
 *       the DB, fetching at most {@code riot.fetch-batch-size} new matches per
 *       call. Where the previous call stopped is kept in {@code backfillOffset},
 *       so the next call re-reads page 0 (new games) and then jumps there
 *       instead of re-scanning the whole list. When the walk reaches the end of
 *       the season with nothing missing, the player is marked
 *       {@code backfillComplete}.</li>
 *   <li><b>Head check</b> (backfill complete): one match-id call with
 *       {@code startTime} = the newest match we know of. If every returned id is
 *       already stored, the player is up to date — 1 call total instead of
 *       re-walking the whole season. New ids are fetched; if there are more
 *       than one batch, or a fetch fails, the player drops back to backfill so
 *       nothing is skipped.</li>
 * </ul>
 *
 * <p>Every match is saved in its own transaction (no DB transaction is held
 * across the network), and each newly stored match is handed to
 * {@link DiscoveryService}. The caller persists the player's updated sync state
 * ({@code backfillComplete}, {@code newestMatchAt}).
 */
@Service
public class FetchService {

    private static final Logger log = LoggerFactory.getLogger(FetchService.class);

    /** Outcome of one sync: how many matches were stored and whether the season is now complete. */
    public record SyncResult(int stored, boolean backfillComplete) {
    }

    private final RiotApiClient riot;
    private final MatchRepository matches;
    private final RiotProperties props;
    private final DiscoveryService discovery;

    public FetchService(RiotApiClient riot, MatchRepository matches, RiotProperties props,
                        DiscoveryService discovery) {
        this.riot = riot;
        this.matches = matches;
        this.props = props;
        this.discovery = discovery;
    }

    /** Sync and return the number of new matches stored. */
    public int fetchAndStore(Player player) {
        return sync(player).stored();
    }

    /**
     * Bring the player's stored history up to date (see class docs). Updates
     * {@code backfillComplete} and {@code newestMatchAt} on {@code player};
     * the caller saves it.
     */
    public SyncResult sync(Player player) {
        if (player.isBackfillComplete() && player.getNewestMatchAt() != null) {
            SyncResult head = headCheck(player);
            if (head != null) {
                return head;
            }
        }
        return backfill(player);
    }

    /**
     * One match-id call since the newest known match. Returns {@code null} when
     * the gap is too big for a head check and a backfill walk is needed instead.
     */
    private SyncResult headCheck(Player player) {
        String tag = tag(player);
        int budget = budget();
        int pageSize = pageSize();
        long seasonStartSec = props.getCurrentSeasonStartEpochMs() / 1000L;
        // Inclusive: the newest known match usually comes back too and is skipped
        // as already stored. Overlap is harmless; a gap would lose games.
        long sinceSec = Math.max(seasonStartSec, player.getNewestMatchAt().getEpochSecond());

        List<String> ids = riot.getMatchIds(player.getPuuid(), 0, pageSize, sinceSec);
        List<String> missing = new ArrayList<>();
        for (String id : ids) {
            if (!matches.existsById(id)) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            log.debug("[{}] up to date (head check: {} known id(s) since {})", tag, ids.size(),
                    player.getNewestMatchAt());
            return new SyncResult(0, true);
        }
        if (missing.size() > budget || ids.size() >= pageSize) {
            log.info("[{}] {} new match(es) since last check — more than one batch, switching to backfill",
                    tag, missing.size());
            player.setBackfillComplete(false);
            return null;
        }

        int stored = 0;
        boolean failed = false;
        for (String matchId : missing) {
            if (store(player, matchId, tag, stored + 1, missing.size())) {
                stored++;
            } else {
                failed = true;
            }
        }
        updateNewest(player, ids.get(0));
        if (failed) {
            // Re-walk next time so the failed match is retried instead of skipped.
            player.setBackfillComplete(false);
        }
        log.info("[{}] head check — {} new match(es) stored", tag, stored);
        return new SyncResult(stored, player.isBackfillComplete());
    }

    private SyncResult backfill(Player player) {
        String tag = tag(player);
        int budget = budget();
        int pageSize = pageSize();
        int maxStart = Math.max(pageSize, props.getMaxHistoryMatches());
        long seasonStartSec = props.getCurrentSeasonStartEpochMs() / 1000L;
        int resumeAt = Math.max(0, player.getBackfillOffset());
        int stored = 0;
        int scanned = 0;
        boolean budgetHit = false;
        boolean reachedEnd = false;
        Integer firstFailedPage = null;
        String newestId = null;
        long startedAt = System.currentTimeMillis();

        log.info("[{}] backfill — will pull up to {} new match(es) this cycle (resuming near offset {})",
                tag, budget, resumeAt);

        int start = 0;
        while (true) {
            if (start >= maxStart) {
                reachedEnd = true; // safety cap: treat as the end of what we keep
                break;
            }
            List<String> ids = riot.getMatchIds(player.getPuuid(), start, pageSize, seasonStartSec);
            if (ids.isEmpty()) {
                reachedEnd = true;
                break;
            }
            if (start == 0) {
                newestId = ids.get(0);
            }
            scanned += ids.size();
            boolean pageHadKnownMatch = false;
            for (String matchId : ids) {
                if (stored >= budget) {
                    budgetHit = true;
                    break;
                }
                if (matches.existsById(matchId)) {
                    pageHadKnownMatch = true;
                    continue;
                }
                if (store(player, matchId, tag, stored + 1, budget)) {
                    stored++;
                } else if (firstFailedPage == null) {
                    firstFailedPage = start;
                }
            }
            if (budgetHit) {
                break; // resume on this page next time
            }
            if (ids.size() < pageSize) {
                reachedEnd = true; // short page: oldest match of the season
                break;
            }
            int next = start + pageSize;
            if (stored >= budget) {
                budgetHit = true;
                start = next; // this page is done; resume on the next one
                break;
            }
            // Page 0 always runs (it catches games played since the last cycle);
            // then jump past pages finished in earlier cycles. New games only push
            // older ids to higher offsets, so one page of overlap is enough — as
            // long as page 0 shows fewer than a page of new games.
            if (start == 0 && pageHadKnownMatch && resumeAt - pageSize > next) {
                next = resumeAt - pageSize;
            }
            start = next;
        }

        boolean complete = reachedEnd && firstFailedPage == null;
        int cursor = complete ? 0 : (budgetHit ? start : 0);
        if (firstFailedPage != null) {
            cursor = Math.min(cursor, firstFailedPage);
        }
        player.setBackfillComplete(complete);
        player.setBackfillOffset(cursor);
        if (newestId != null) {
            updateNewest(player, newestId);
        }

        long took = System.currentTimeMillis() - startedAt;
        log.info("[{}] backfill — {} new match(es) stored, {} id(s) scanned in {} ms{}",
                tag, stored, scanned, took, complete ? "; season complete" : "");
        return new SyncResult(stored, complete);
    }

    /** Fetch, persist and run discovery for one match. Returns false on failure. */
    private boolean store(Player player, String matchId, String tag, int n, int of) {
        try {
            JsonNode json = riot.getMatch(matchId);
            MatchEntity match = parse(matchId, json);
            if (match == null) {
                return false;
            }
            matches.save(match);
            log.info("[{}] +{} {} ({}/{})", tag, matchId, describe(match, player.getPuuid()), n, of);
            try {
                discovery.onMatchStored(match, player);
            } catch (Exception e) {
                log.warn("[{}] discovery failed for match {}: {}", tag, matchId, e.getMessage());
            }
            return true;
        } catch (RiotApiException e) {
            if (e.isDecryptionError()) {
                throw e; // stale PUUID: let the caller re-key and retry
            }
            log.warn("[{}] failed to fetch/persist match {}: {}", tag, matchId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("[{}] failed to fetch/persist match {}: {}", tag, matchId, e.getMessage());
            return false;
        }
    }

    private void updateNewest(Player player, String newestId) {
        matches.findGameCreation(newestId)
                .map(Instant::ofEpochMilli)
                .filter(t -> player.getNewestMatchAt() == null || t.isAfter(player.getNewestMatchAt()))
                .ifPresent(player::setNewestMatchAt);
    }

    private int budget() {
        return Math.max(1, props.getFetchBatchSize());
    }

    private int pageSize() {
        return Math.min(100, Math.max(1, props.getMatchPageSize()));
    }

    private static String tag(Player player) {
        return player.getGameName() + "#" + player.getTagLine();
    }

    /** Short one-line summary of the player's line in a match, for logs. */
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
