package com.riotanalizer.service;

import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.client.dto.LeagueEntryDto;
import com.riotanalizer.domain.Player;
import com.riotanalizer.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Solo-queue rank for live-game participants. Looks in an in-memory cache first,
 * then at a recently refreshed {@link Player} row, and only then asks Riot
 * (league-v4 by-puuid). Failed lookups are not cached and resolve to empty, so a
 * rank problem never breaks the live view.
 */
@Component
public class LiveRankResolver {

    private static final Logger log = LoggerFactory.getLogger(LiveRankResolver.class);
    static final String SOLO_QUEUE = "RANKED_SOLO_5x5";
    static final Duration TTL = Duration.ofMinutes(30);
    private static final int PRUNE_THRESHOLD = 2_000;

    /** Solo rank; {@code tier == null} means the player is unranked in solo queue. */
    public record SoloRank(String tier, String rank, Integer leaguePoints) {
        public static final SoloRank UNRANKED = new SoloRank(null, null, null);

        public boolean isUnranked() {
            return tier == null;
        }
    }

    private record Entry(SoloRank rank, Instant at) {
    }

    private final RiotApiClient riot;
    private final PlayerRepository players;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    @Autowired
    public LiveRankResolver(RiotApiClient riot, PlayerRepository players) {
        this(riot, players, Clock.systemUTC());
    }

    LiveRankResolver(RiotApiClient riot, PlayerRepository players, Clock clock) {
        this.riot = riot;
        this.players = players;
        this.clock = clock;
    }

    public Optional<SoloRank> resolve(String puuid) {
        if (puuid == null || puuid.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Entry hit = cache.get(puuid);
        if (hit != null && fresh(hit.at(), now)) {
            return Optional.of(hit.rank());
        }

        Optional<SoloRank> known = players.findById(puuid)
                .filter(p -> p.getLastFetched() != null && fresh(p.getLastFetched(), now))
                .map(LiveRankResolver::fromPlayer);
        if (known.isPresent()) {
            put(puuid, known.get(), now);
            return known;
        }

        try {
            SoloRank rank = fromEntries(riot.getLeagueEntriesByPuuid(puuid));
            put(puuid, rank, now);
            return Optional.of(rank);
        } catch (RuntimeException e) {
            log.debug("Solo rank lookup failed for a live participant: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static SoloRank fromEntries(List<LeagueEntryDto> entries) {
        if (entries == null) {
            return SoloRank.UNRANKED;
        }
        return entries.stream()
                .filter(e -> SOLO_QUEUE.equals(e.queueType()) && e.tier() != null)
                .findFirst()
                .map(e -> new SoloRank(e.tier(), e.rank(), e.leaguePoints()))
                .orElse(SoloRank.UNRANKED);
    }

    private static SoloRank fromPlayer(Player p) {
        return p.getSoloTier() == null
                ? SoloRank.UNRANKED
                : new SoloRank(p.getSoloTier(), p.getSoloRank(), p.getSoloLeaguePoints());
    }

    private void put(String puuid, SoloRank rank, Instant now) {
        if (cache.size() > PRUNE_THRESHOLD) {
            cache.values().removeIf(e -> !fresh(e.at(), now));
        }
        cache.put(puuid, new Entry(rank, now));
    }

    private static boolean fresh(Instant at, Instant now) {
        return at.plus(TTL).isAfter(now);
    }
}
