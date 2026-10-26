package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import com.riotanalizer.dto.FetchQueueDto;
import com.riotanalizer.dto.FetchQueueEntryDto;
import com.riotanalizer.dto.RateLimitDto;
import com.riotanalizer.ratelimit.RiotRateLimitObserver;
import com.riotanalizer.ratelimit.RiotRateLimiter;
import com.riotanalizer.repository.PlayerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read-only view of the background scheduler: who is being checked now, who
 * comes next (in the exact order {@link ScheduledFetcher#nextPick} picks them),
 * and who becomes due later. Makes no Riot calls.
 */
@Service
public class FetchQueueService {

    private static final int MAX_LIMIT = 100;

    private final PlayerRepository players;
    private final ScheduledFetcher fetcher;
    private final LiveGameService liveGames;
    private final RiotProperties props;
    private final RiotRateLimiter rateLimiter;
    private final RiotRateLimitObserver rateLimitObserver;
    private final EarlyCheckPolicy earlyPolicy;
    private final long tickMs;

    public FetchQueueService(PlayerRepository players, ScheduledFetcher fetcher, LiveGameService liveGames,
                             RiotProperties props, RiotRateLimiter rateLimiter,
                             RiotRateLimitObserver rateLimitObserver, EarlyCheckPolicy earlyPolicy,
                             @Value("${riot.scheduler-tick-ms:15000}") long tickMs) {
        this.players = players;
        this.fetcher = fetcher;
        this.liveGames = liveGames;
        this.props = props;
        this.rateLimiter = rateLimiter;
        this.rateLimitObserver = rateLimitObserver;
        this.earlyPolicy = earlyPolicy;
        this.tickMs = tickMs;
    }

    public FetchQueueDto snapshot(int limit) {
        int n = Math.min(MAX_LIMIT, Math.max(1, limit));
        Instant now = Instant.now();
        Optional<ScheduledFetcher.Running> running = fetcher.running();
        String runningPuuid = running.map(ScheduledFetcher.Running::puuid).orElse(null);
        boolean earlyTrackedOpen = fetcher.earlyAllowed(TrackingLevel.TRACKED);
        boolean earlyDiscoveredOpen = fetcher.earlyAllowed(TrackingLevel.DISCOVERED);

        // Same order as ScheduledFetcher.nextPick: due TRACKED, early TRACKED (if the budget
        // allows), due DISCOVERED, early DISCOVERED (if the budget allows). The running player
        // still looks due / early-eligible (its schedule is written when it finishes), so it is
        // left out of the queue and shown separately.
        List<FetchQueueEntryDto> next = new ArrayList<>();
        for (TrackingLevel level : List.of(TrackingLevel.TRACKED, TrackingLevel.DISCOVERED)) {
            addAll(next, players.findDue(level, now, PageRequest.of(0, n + 1)), false, runningPuuid, n, now);
            boolean open = level == TrackingLevel.TRACKED ? earlyTrackedOpen : earlyDiscoveredOpen;
            if (open) {
                addAll(next, players.findEarlyEligible(level, now, PageRequest.of(0, n + 1)), true, runningPuuid,
                        n, now);
            }
        }
        boolean runningEarly = running.map(ScheduledFetcher.Running::early).orElse(false);
        TrackingLevel runningLevel = running.map(ScheduledFetcher.Running::level).orElse(null);
        long runningDueTracked = !runningEarly && runningLevel == TrackingLevel.TRACKED ? 1 : 0;
        long runningDueDiscovered = !runningEarly && runningLevel == TrackingLevel.DISCOVERED ? 1 : 0;
        long runningEarlyTracked = runningEarly && runningLevel == TrackingLevel.TRACKED ? 1 : 0;
        long runningEarlyDiscovered = runningEarly && runningLevel == TrackingLevel.DISCOVERED ? 1 : 0;
        RiotRateLimiter.WindowStatus longWindow = rateLimiter.status().longWindow();

        return new FetchQueueDto(
                now.toString(),
                tickMs,
                fetcher.lastRunAt().map(Instant::toString).orElse(null),
                fetcher.lastRunChecked(),
                running.map(r -> new FetchQueueDto.Running(r.puuid(), r.gameName(), r.tagLine(), r.level().name(),
                        r.startedAt().toString(),
                        runningCheckType(r).name(), r.early()))
                        .orElse(null),
                Math.max(0, players.countDue(TrackingLevel.TRACKED, now) - runningDueTracked),
                Math.max(0, players.countDue(TrackingLevel.DISCOVERED, now) - runningDueDiscovered),
                Math.max(0, players.countEarlyEligible(TrackingLevel.TRACKED, now) - runningEarlyTracked),
                Math.max(0, players.countEarlyEligible(TrackingLevel.DISCOVERED, now) - runningEarlyDiscovered),
                new FetchQueueDto.EarlyChecks(
                        earlyPolicy.enabled(),
                        longWindow.used(),
                        longWindow.limit(),
                        earlyPolicy.minFree(TrackingLevel.TRACKED, longWindow.limit()),
                        earlyPolicy.minFree(TrackingLevel.DISCOVERED, longWindow.limit()),
                        earlyTrackedOpen,
                        earlyDiscoveredOpen,
                        earlyPolicy.minGap(TrackingLevel.TRACKED).toMillis(),
                        earlyPolicy.minGap(TrackingLevel.DISCOVERED).toMillis()),
                next,
                upcoming(TrackingLevel.TRACKED, n, now),
                upcoming(TrackingLevel.DISCOVERED, n, now),
                rateLimit());
    }

    private FetchQueuePlanner.CheckType runningCheckType(ScheduledFetcher.Running r) {
        if (r.level() == TrackingLevel.DISCOVERED && !fetcher.discoveredFetchMatches()) {
            return FetchQueuePlanner.CheckType.PROFILE_ONLY;
        }
        return r.backfill() ? FetchQueuePlanner.CheckType.BACKFILL : FetchQueuePlanner.CheckType.HEAD_CHECK;
    }

    private RateLimitDto rateLimit() {
        RiotRateLimiter.Status s = rateLimiter.status();
        RateLimitDto.RiotView riot = rateLimitObserver.snapshot().map(o -> new RateLimitDto.RiotView(
                o.observedAt() == null ? null : o.observedAt().toString(),
                o.appWindows().stream()
                        .map(w -> new RateLimitDto.RiotWindow(w.windowSec(), w.count(), w.limit()))
                        .toList(),
                o.rateLimitedCount(),
                o.lastRateLimited() == null ? null : o.lastRateLimited().at().toString(),
                o.lastRateLimited() == null ? null : o.lastRateLimited().retryAfterSec(),
                o.lastRateLimited() == null ? null : o.lastRateLimited().type())).orElse(null);
        return new RateLimitDto(
                window(s.shortWindow()),
                window(s.longWindow()),
                s.priorities().stream()
                        .map(p -> new RateLimitDto.Priority(p.priority().name(), p.longLimit(), p.reserve(),
                                p.freeInMs(), p.waiting()))
                        .toList(),
                riot);
    }

    private static RateLimitDto.Window window(RiotRateLimiter.WindowStatus w) {
        return new RateLimitDto.Window(w.used(), w.limit(), w.windowMs(), w.nextExpiryInMs());
    }

    private void addAll(List<FetchQueueEntryDto> next, List<Player> candidates, boolean early,
                        String runningPuuid, int n, Instant now) {
        for (Player p : candidates) {
            if (next.size() >= n) {
                return;
            }
            if (!p.getPuuid().equals(runningPuuid)) {
                next.add(entry(p, next.size() + 1, early, now));
            }
        }
    }

    private List<FetchQueueEntryDto> upcoming(TrackingLevel level, int n, Instant now) {
        return players.findUpcoming(level, now, PageRequest.of(0, n)).stream()
                .map(p -> entry(p, null, false, now))
                .toList();
    }

    private FetchQueueEntryDto entry(Player p, Integer position, boolean early, Instant now) {
        Boolean inGame = p.isTracked() ? liveGames.cachedInGame(p.getPuuid()).orElse(null) : null;
        FetchQueuePlanner.Plan plan = FetchQueuePlanner.plan(new FetchQueuePlanner.State(
                p.getTrackingLevel(), p.isBackfillComplete(), p.getBackfillOffset(), p.getNewestMatchAt(),
                p.getLastFetched(), inGame, p.getNextCheckAt()), Math.max(1, props.getFetchBatchSize()),
                fetcher.discoveredFetchMatches(), now);
        return new FetchQueueEntryDto(
                position,
                early,
                p.getPuuid(),
                p.getGameName(),
                p.getTagLine(),
                p.getProfileIconId() == null ? 0 : p.getProfileIconId(),
                p.getTrackingLevel().name(),
                p.getDiscoveryDepth(),
                iso(p.getNextCheckAt()),
                iso(p.getEarlyCheckAt()),
                iso(p.getNewestMatchAt()),
                plan.checkType().name(),
                plan.steps(),
                plan.minCalls(),
                plan.maxCalls(),
                early ? earlyReason(p, now) + " · " + plan.reason() : plan.reason(),
                inGame);
    }

    private static String earlyReason(Player p, Instant now) {
        return p.getNextCheckAt() == null
                ? "Early check with spare budget"
                : "Early check with spare budget (regular check in "
                + FetchQueuePlanner.human(java.time.Duration.between(now, p.getNextCheckAt())) + ")";
    }

    private static String iso(Instant t) {
        return t == null ? null : t.toString();
    }
}
