package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import com.riotanalizer.dto.FetchQueueDto;
import com.riotanalizer.dto.FetchQueueEntryDto;
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
 * comes next (in the exact order {@link ScheduledFetcher#nextDue} picks them),
 * and who becomes due later. Makes no Riot calls.
 */
@Service
public class FetchQueueService {

    private static final int MAX_LIMIT = 100;

    private final PlayerRepository players;
    private final ScheduledFetcher fetcher;
    private final LiveGameService liveGames;
    private final RiotProperties props;
    private final long tickMs;

    public FetchQueueService(PlayerRepository players, ScheduledFetcher fetcher, LiveGameService liveGames,
                             RiotProperties props, @Value("${riot.scheduler-tick-ms:15000}") long tickMs) {
        this.players = players;
        this.fetcher = fetcher;
        this.liveGames = liveGames;
        this.props = props;
        this.tickMs = tickMs;
    }

    public FetchQueueDto snapshot(int limit) {
        int n = Math.min(MAX_LIMIT, Math.max(1, limit));
        Instant now = Instant.now();
        Optional<ScheduledFetcher.Running> running = fetcher.running();
        String runningPuuid = running.map(ScheduledFetcher.Running::puuid).orElse(null);

        // Same order as ScheduledFetcher.nextDue: every due TRACKED player, then DISCOVERED.
        // The running player still looks due (its next_check_at is written when it finishes),
        // so it is left out of the queue and shown separately.
        List<Player> due = new ArrayList<>(players.findDue(TrackingLevel.TRACKED, now, PageRequest.of(0, n + 1)));
        if (due.size() <= n) {
            due.addAll(players.findDue(TrackingLevel.DISCOVERED, now, PageRequest.of(0, n + 1 - due.size())));
        }
        List<FetchQueueEntryDto> next = new ArrayList<>();
        long runningDueTracked = 0;
        long runningDueDiscovered = 0;
        for (Player p : due) {
            if (p.getPuuid().equals(runningPuuid)) {
                if (p.isTracked()) {
                    runningDueTracked = 1;
                } else {
                    runningDueDiscovered = 1;
                }
                continue;
            }
            if (next.size() < n) {
                next.add(entry(p, next.size() + 1, now));
            }
        }

        return new FetchQueueDto(
                now.toString(),
                tickMs,
                fetcher.lastRunAt().map(Instant::toString).orElse(null),
                fetcher.lastRunChecked(),
                running.map(r -> new FetchQueueDto.Running(r.puuid(), r.gameName(), r.tagLine(), r.level().name(),
                        r.startedAt().toString(),
                        (r.backfill() ? FetchQueuePlanner.CheckType.BACKFILL
                                : FetchQueuePlanner.CheckType.HEAD_CHECK).name()))
                        .orElse(null),
                Math.max(0, players.countDue(TrackingLevel.TRACKED, now) - runningDueTracked),
                Math.max(0, players.countDue(TrackingLevel.DISCOVERED, now) - runningDueDiscovered),
                next,
                upcoming(TrackingLevel.TRACKED, n, now),
                upcoming(TrackingLevel.DISCOVERED, n, now));
    }

    private List<FetchQueueEntryDto> upcoming(TrackingLevel level, int n, Instant now) {
        return players.findUpcoming(level, now, PageRequest.of(0, n)).stream()
                .map(p -> entry(p, null, now))
                .toList();
    }

    private FetchQueueEntryDto entry(Player p, Integer position, Instant now) {
        Boolean inGame = p.isTracked() ? liveGames.cachedInGame(p.getPuuid()).orElse(null) : null;
        FetchQueuePlanner.Plan plan = FetchQueuePlanner.plan(new FetchQueuePlanner.State(
                p.getTrackingLevel(), p.isBackfillComplete(), p.getBackfillOffset(), p.getNewestMatchAt(),
                p.getLastFetched(), inGame, p.getNextCheckAt()), Math.max(1, props.getFetchBatchSize()), now);
        return new FetchQueueEntryDto(
                position,
                p.getPuuid(),
                p.getGameName(),
                p.getTagLine(),
                p.getProfileIconId() == null ? 0 : p.getProfileIconId(),
                p.getTrackingLevel().name(),
                p.getDiscoveryDepth(),
                iso(p.getNextCheckAt()),
                iso(p.getNewestMatchAt()),
                plan.checkType().name(),
                plan.steps(),
                plan.minCalls(),
                plan.maxCalls(),
                plan.reason(),
                inGame);
    }

    private static String iso(Instant t) {
        return t == null ? null : t.toString();
    }
}
