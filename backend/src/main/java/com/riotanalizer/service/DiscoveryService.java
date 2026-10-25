package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.MatchEntity;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import com.riotanalizer.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the other participants of a freshly stored match into DISCOVERED
 * players, so their history is backfilled with spare rate-limit budget.
 * Costs no Riot calls: name and PUUID come from the stored match.
 */
@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private final PlayerRepository players;
    private final DiscoveryPolicy policy;

    public DiscoveryService(PlayerRepository players, RiotProperties props) {
        this(players, new DiscoveryPolicy(props.getDiscoveryMaxDepth(), props.getDiscoveryMaxPlayers()));
    }

    DiscoveryService(PlayerRepository players, DiscoveryPolicy policy) {
        this.players = players;
        this.policy = policy;
    }

    /** Returns how many players were newly discovered or moved closer. */
    public int onMatchStored(MatchEntity match, Player source) {
        int depth = policy.childDepth(source);
        if (depth < 0 || match == null) {
            return 0;
        }
        Map<String, Participant> others = new LinkedHashMap<>();
        for (Participant p : match.getParticipants()) {
            if (p.getPuuid() != null && !p.getPuuid().equals(source.getPuuid())) {
                others.put(p.getPuuid(), p);
            }
        }
        if (others.isEmpty()) {
            return 0;
        }
        Map<String, Player> existing = new HashMap<>();
        for (Player p : players.findAllById(others.keySet())) {
            existing.put(p.getPuuid(), p);
        }
        long discovered = policy.maxPlayers() > 0 ? players.countByTrackingLevel(TrackingLevel.DISCOVERED) : 0;

        List<Player> changed = new ArrayList<>();
        for (Participant part : others.values()) {
            Player player = existing.get(part.getPuuid());
            boolean becomesDiscovered = player == null || player.getTrackingLevel() == TrackingLevel.NONE;
            if (becomesDiscovered && !policy.hasRoomFor(discovered)) {
                continue;
            }
            if (player == null) {
                player = new Player();
                player.setPuuid(part.getPuuid());
                player.setGameName(part.getRiotIdGameName());
                player.setTagLine(part.getRiotIdTagLine());
            }
            if (DiscoveryPolicy.applyTo(player, depth)) {
                changed.add(player);
                if (becomesDiscovered) {
                    discovered++;
                }
            }
        }
        if (!changed.isEmpty()) {
            players.saveAll(changed);
            log.debug("[{}#{}] match {} discovered {} player(s) at depth {}", source.getGameName(),
                    source.getTagLine(), match.getMatchId(), changed.size(), depth);
        }
        return changed.size();
    }
}
