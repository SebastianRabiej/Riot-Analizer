package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On startup, if {@code riot.initial-player} ("gameName#tagLine") is configured,
 * register and track it and pull an initial batch of matches.
 */
@Component
public class InitialPlayerRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialPlayerRegistrar.class);

    private final RiotProperties props;
    private final PlayerService playerService;

    public InitialPlayerRegistrar(RiotProperties props, PlayerService playerService) {
        this.props = props;
        this.playerService = playerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String initial = props.getInitialPlayer();
        if (initial == null || initial.isBlank()) {
            return;
        }
        int hash = initial.lastIndexOf('#');
        if (hash <= 0 || hash == initial.length() - 1) {
            log.warn("riot.initial-player '{}' is not in gameName#tagLine format; skipping", initial);
            return;
        }
        String gameName = initial.substring(0, hash).trim();
        String tagLine = initial.substring(hash + 1).trim();
        try {
            playerService.track(gameName, tagLine);
            log.info("Registered and tracked initial player {}#{}", gameName, tagLine);
        } catch (Exception e) {
            log.warn("Failed to register initial player {}#{}: {}", gameName, tagLine, e.getMessage());
        }
    }
}
