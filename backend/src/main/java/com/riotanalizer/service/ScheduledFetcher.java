package com.riotanalizer.service;

import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.exception.RiotApiException;
import com.riotanalizer.ratelimit.FetchContext;
import com.riotanalizer.ratelimit.FetchPriority;
import com.riotanalizer.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Background fetching, one player at a time, always picking the most important
 * due work first:
 * <ol>
 *   <li>(user requests — refresh, analysis — don't go through here; they simply
 *       outrank everything below at the rate limiter)</li>
 *   <li>TRACKED players whose {@code next_check_at} is due, most overdue first</li>
 *   <li>only when no tracked player is due: DISCOVERED players, closest
 *       discovery depth first, then most overdue</li>
 * </ol>
 * The choice is re-made after every player, so a tracked player that becomes
 * due while discovered backfill is running is served next.
 *
 * <p>A check spends as few Riot calls as the player's state allows (see
 * {@link FetchService} and {@link CheckCadence}): usually just the 1-call head
 * check; profile/rank only after new games or when old; live game for tracked
 * players only. Afterwards {@code next_check_at} is set from how recently the
 * player played, so idle players are rarely touched.
 */
@Component
public class ScheduledFetcher {

    private static final Logger log = LoggerFactory.getLogger(ScheduledFetcher.class);

    /** Upper bound per tick so the loop periodically yields (and logs) even with a large backlog. */
    private static final int MAX_PLAYERS_PER_TICK = 200;

    private static final List<TrackingLevel> ORDER = List.of(TrackingLevel.TRACKED, TrackingLevel.DISCOVERED);

    private final PlayerRepository players;
    private final PlayerService playerService;
    private final FetchService fetchService;
    private final LiveGameService liveGameService;
    private final PuuidMigrationGate migrationGate;

    public ScheduledFetcher(PlayerRepository players, PlayerService playerService, FetchService fetchService,
                            LiveGameService liveGameService, PuuidMigrationGate migrationGate) {
        this.players = players;
        this.playerService = playerService;
        this.fetchService = fetchService;
        this.liveGameService = liveGameService;
        this.migrationGate = migrationGate;
    }

    @Scheduled(fixedDelayString = "${riot.scheduler-tick-ms:15000}")
    public void run() {
        // Wait for the startup PUUID key check so we never call Riot with stale PUUIDs.
        if (!migrationGate.awaitOpen()) {
            return;
        }
        int tracked = 0;
        int discovered = 0;
        for (int i = 0; i < MAX_PLAYERS_PER_TICK; i++) {
            Optional<Player> next = nextDue(Instant.now());
            if (next.isEmpty()) {
                break;
            }
            Player player = next.get();
            if (player.isTracked()) {
                tracked++;
            } else {
                discovered++;
            }
            process(player);
        }
        if (tracked + discovered > 0) {
            log.info("Scheduler tick: checked {} tracked and {} discovered player(s)", tracked, discovered);
        }
    }

    /** The single most important player that is due now, if any. */
    Optional<Player> nextDue(Instant now) {
        for (TrackingLevel level : ORDER) {
            List<Player> due = players.findDue(level, now, PageRequest.of(0, 1));
            if (!due.isEmpty()) {
                return Optional.of(due.get(0));
            }
        }
        return Optional.empty();
    }

    void process(Player player) {
        TrackingLevel level = player.getTrackingLevel();
        FetchPriority priority = level == TrackingLevel.TRACKED
                ? FetchPriority.TRACKED_HISTORY
                : FetchPriority.DISCOVERED_HISTORY;
        try {
            FetchContext.run(priority, () -> check(player));
        } catch (NotFoundException e) {
            log.warn("[{}] not found at Riot; next try in {}", tag(player), CheckCadence.NOT_FOUND_BACKOFF);
            backOff(player, CheckCadence.NOT_FOUND_BACKOFF);
        } catch (Exception e) {
            Duration backoff = CheckCadence.errorBackoff(level);
            log.warn("[{}] scheduled check failed ({}); next try in {}", tag(player), e.getMessage(), backoff);
            backOff(player, backoff);
        }
    }

    private void check(Player original) {
        Player player = original;
        boolean profileRefreshed = false;
        FetchService.SyncResult result;
        try {
            result = fetchService.sync(player);
        } catch (RiotApiException e) {
            if (!e.isDecryptionError()) {
                throw e;
            }
            // PUUID issued for another API key: refreshProfile re-keys, then retry once.
            player = playerService.refreshProfile(player);
            profileRefreshed = true;
            result = fetchService.sync(player);
        }

        Instant now = Instant.now();
        if (!profileRefreshed && CheckCadence.needsProfileRefresh(
                player.getTrackingLevel(), player.getLastFetched(), result.stored(), now)) {
            player = playerService.refreshProfile(player); // persists sync state too
        }

        boolean inGame = false;
        if (player.isTracked()) {
            inGame = liveGameService.refresh(player.getPuuid()).inGame();
        }

        Duration delay = CheckCadence.nextDelay(player.getTrackingLevel(), player.isBackfillComplete(),
                result.stored(), player.getNewestMatchAt(), inGame, now);
        player.setNextCheckAt(now.plus(delay));
        playerService.save(player);

        if (result.stored() > 0 || delay.isZero()) {
            log.info("[{}] {} — {} new match(es){}; next check in {}", tag(player), player.getTrackingLevel(),
                    result.stored(), inGame ? ", in game" : "", delay);
        } else {
            log.info("[{}] {} up to date; next check in {}", tag(player), player.getTrackingLevel(), delay);
        }
    }

    private void backOff(Player player, Duration delay) {
        try {
            // Reload: the in-memory copy may be half-updated, or its PUUID may
            // have just been re-keyed away (saving it would resurrect the old row).
            players.findById(player.getPuuid()).ifPresent(fresh -> {
                fresh.setNextCheckAt(Instant.now().plus(delay));
                playerService.save(fresh);
            });
        } catch (Exception e) {
            log.warn("[{}] could not store back-off: {}", tag(player), e.getMessage());
        }
    }

    private static String tag(Player p) {
        return p.getGameName() + "#" + p.getTagLine();
    }
}
