package com.riotanalizer.service;

import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.exception.RiotApiException;
import com.riotanalizer.ratelimit.FetchContext;
import com.riotanalizer.ratelimit.FetchPriority;
import com.riotanalizer.ratelimit.RiotRateLimiter;
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
 *   <li><b>early</b> TRACKED checks — only while the Riot budget has plenty of
 *       headroom: tracked players not due yet, soonest regular check first
 *       (see {@link EarlyCheckPolicy})</li>
 *   <li>DISCOVERED players whose {@code next_check_at} is due, closest
 *       discovery depth first, then most overdue</li>
 *   <li><b>early</b> DISCOVERED checks, with an even bigger headroom requirement</li>
 * </ol>
 * The choice is re-made after every player, so a tracked player that becomes
 * due while discovered backfill is running is served next, and early checks
 * stop as soon as the budget fills up.
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
    private static final PageRequest ONE = PageRequest.of(0, 1);

    private final PlayerRepository players;
    private final PlayerService playerService;
    private final FetchService fetchService;
    private final LiveGameService liveGameService;
    private final RiotRateLimiter rateLimiter;
    private final EarlyCheckPolicy earlyPolicy;

    /** The player being checked right now (for the fetch-queue view); null when idle. */
    public record Running(String puuid, String gameName, String tagLine, TrackingLevel level,
                          boolean backfill, boolean early, Instant startedAt) {
    }

    /** The next player to check, and whether it is an early (spare-budget) check. */
    public record Pick(Player player, boolean early) {
    }

    private volatile Running running;
    private volatile Instant lastRunAt;
    private volatile int lastRunChecked;

    public ScheduledFetcher(PlayerRepository players, PlayerService playerService, FetchService fetchService,
                            LiveGameService liveGameService, RiotRateLimiter rateLimiter,
                            EarlyCheckPolicy earlyPolicy) {
        this.players = players;
        this.playerService = playerService;
        this.fetchService = fetchService;
        this.liveGameService = liveGameService;
        this.rateLimiter = rateLimiter;
        this.earlyPolicy = earlyPolicy;
    }

    @Scheduled(fixedDelayString = "${riot.scheduler-tick-ms:15000}")
    public void run() {
        int tracked = 0;
        int discovered = 0;
        int early = 0;
        for (int i = 0; i < MAX_PLAYERS_PER_TICK; i++) {
            Optional<Pick> next = nextPick(Instant.now());
            if (next.isEmpty()) {
                break;
            }
            Player player = next.get().player();
            boolean isEarly = next.get().early();
            if (player.isTracked()) {
                tracked++;
            } else {
                discovered++;
            }
            if (isEarly) {
                early++;
            }
            running = new Running(player.getPuuid(), player.getGameName(), player.getTagLine(),
                    player.getTrackingLevel(), !player.isBackfillComplete() || player.getNewestMatchAt() == null,
                    isEarly, Instant.now());
            try {
                process(player, isEarly);
            } finally {
                running = null;
            }
        }
        lastRunAt = Instant.now();
        lastRunChecked = tracked + discovered;
        if (tracked + discovered > 0) {
            log.info("Scheduler tick: checked {} tracked and {} discovered player(s) ({} early)",
                    tracked, discovered, early);
        }
    }

    public Optional<Running> running() {
        return Optional.ofNullable(running);
    }

    public Optional<Instant> lastRunAt() {
        return Optional.ofNullable(lastRunAt);
    }

    public int lastRunChecked() {
        return lastRunChecked;
    }

    /**
     * The single most important player to check now, if any: due players of
     * each level, each followed by that level's early checks while the budget
     * allows them (see class docs for the order).
     */
    Optional<Pick> nextPick(Instant now) {
        for (TrackingLevel level : ORDER) {
            List<Player> due = players.findDue(level, now, ONE);
            if (!due.isEmpty()) {
                return Optional.of(new Pick(due.get(0), false));
            }
            if (earlyAllowed(level)) {
                List<Player> early = players.findEarlyEligible(level, now, ONE);
                if (!early.isEmpty()) {
                    return Optional.of(new Pick(early.get(0), true));
                }
            }
        }
        return Optional.empty();
    }

    /** Whether the Riot budget has enough headroom for an early check at {@code level} right now. */
    public boolean earlyAllowed(TrackingLevel level) {
        if (!earlyPolicy.enabled()) {
            return false;
        }
        RiotRateLimiter.WindowStatus w = rateLimiter.status().longWindow();
        return earlyPolicy.budgetAllows(level, w.used(), w.limit());
    }

    void process(Player player, boolean early) {
        TrackingLevel level = player.getTrackingLevel();
        FetchPriority priority = level == TrackingLevel.TRACKED
                ? FetchPriority.TRACKED_HISTORY
                : FetchPriority.DISCOVERED_HISTORY;
        try {
            FetchContext.run(priority, () -> check(player, early));
        } catch (NotFoundException e) {
            log.warn("[{}] not found at Riot; next try in {}", tag(player), CheckCadence.NOT_FOUND_BACKOFF);
            backOff(player, CheckCadence.NOT_FOUND_BACKOFF);
        } catch (Exception e) {
            Duration backoff = CheckCadence.errorBackoff(level);
            log.warn("[{}] scheduled check failed ({}); next try in {}", tag(player), e.getMessage(), backoff);
            backOff(player, backoff);
        }
    }

    private void check(Player original, boolean early) {
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
        player.setEarlyCheckAt(earlyPolicy.earlyCheckAt(player.getTrackingLevel(), now));
        playerService.save(player);

        String kind = early ? player.getTrackingLevel() + " (early)" : player.getTrackingLevel().name();
        if (result.stored() > 0 || delay.isZero()) {
            log.info("[{}] {} — {} new match(es){}; next check in {}", tag(player), kind,
                    result.stored(), inGame ? ", in game" : "", delay);
        } else {
            log.info("[{}] {} up to date; next check in {}", tag(player), kind, delay);
        }
    }

    private void backOff(Player player, Duration delay) {
        try {
            // Reload: the in-memory copy may be half-updated, or its PUUID may
            // have just been re-keyed away (saving it would resurrect the old row).
            players.findById(player.getPuuid()).ifPresent(fresh -> {
                Instant next = Instant.now().plus(delay);
                fresh.setNextCheckAt(next);
                fresh.setEarlyCheckAt(next); // never cut a back-off short with an early check
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
