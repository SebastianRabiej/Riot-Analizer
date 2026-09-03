package com.riotanalizer.service;

import com.riotanalizer.domain.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically refreshes every tracked player: profile + rank, newest matches,
 * and the live-game cache. Everything runs through the shared rate limiter.
 */
@Component
public class ScheduledFetcher {

    private static final Logger log = LoggerFactory.getLogger(ScheduledFetcher.class);

    private final PlayerService playerService;
    private final FetchService fetchService;
    private final LiveGameService liveGameService;

    public ScheduledFetcher(PlayerService playerService, FetchService fetchService,
                            LiveGameService liveGameService) {
        this.playerService = playerService;
        this.fetchService = fetchService;
        this.liveGameService = liveGameService;
    }

    @Scheduled(fixedDelayString = "${riot.fetch-interval-ms:600000}")
    public void run() {
        List<Player> tracked = playerService.trackedPlayers();
        if (tracked.isEmpty()) {
            return;
        }
        log.info("Scheduled fetch for {} tracked player(s)", tracked.size());
        for (Player player : tracked) {
            try {
                playerService.refreshProfile(player);
                int stored = fetchService.fetchAndStore(player);
                liveGameService.refresh(player.getPuuid());
                log.info("Fetched {}#{}: {} new match(es)",
                        player.getGameName(), player.getTagLine(), stored);
            } catch (Exception e) {
                log.warn("Scheduled fetch failed for {}#{}: {}",
                        player.getGameName(), player.getTagLine(), e.getMessage());
            }
        }
    }
}
