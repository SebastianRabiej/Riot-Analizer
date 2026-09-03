package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.RoleStatDto;
import com.riotanalizer.dto.RolesDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-role (lane/position) performance for the tracked player this season.
 * Position comes from Match-V5 {@code teamPosition}; games without one (ARAM,
 * some remakes) are grouped under "NONE". Optional champion / position filters
 * back the Trends-tab filter bar (e.g. only Vel'Koz games, only Mid games).
 */
@Service
public class RolesService {

    private final ParticipantRepository participants;
    private final RiotProperties props;

    public RolesService(ParticipantRepository participants, RiotProperties props) {
        this.participants = participants;
        this.props = props;
    }

    /** Unfiltered roles (used by the Insights page). */
    @Transactional(readOnly = true)
    public RolesDto roles(Player player, Integer queueId) {
        return roles(player, queueId, null, null);
    }

    @Transactional(readOnly = true)
    public RolesDto roles(Player player, Integer queueId, String champion, String position) {
        List<Participant> parts = participants.findSeasonParticipations(
                player.getPuuid(), props.getCurrentSeasonStartEpochMs(), queueId);

        String champFilter = (champion == null || champion.isBlank()) ? null : champion;
        String posFilter = (position == null || position.isBlank()) ? null : position.toUpperCase();

        Map<String, Agg> byRole = new LinkedHashMap<>();
        int total = 0;
        for (Participant p : parts) {
            if (champFilter != null && !champFilter.equalsIgnoreCase(p.getChampionName())) {
                continue;
            }
            String pos = normalize(p.getTeamPosition());
            if (posFilter != null && !posFilter.equals(pos)) {
                continue;
            }
            total++;
            Agg a = byRole.computeIfAbsent(pos, k -> new Agg());
            a.games++;
            if (p.isWin()) {
                a.wins++;
            }
            a.k += p.getKills();
            a.d += p.getDeaths();
            a.a += p.getAssists();
            a.cs += p.getTotalCs();
            a.vision += p.getVisionScore();
            a.dur += p.getMatch().getGameDuration();
        }

        List<RoleStatDto> roles = new ArrayList<>();
        for (Map.Entry<String, Agg> e : byRole.entrySet()) {
            Agg a = e.getValue();
            roles.add(new RoleStatDto(
                    e.getKey(),
                    a.games,
                    a.wins,
                    a.games - a.wins,
                    StatMath.winRate(a.wins, a.games),
                    StatMath.avg(a.k, a.games),
                    StatMath.avg(a.d, a.games),
                    StatMath.avg(a.a, a.games),
                    StatMath.round2(StatMath.kda(a.k, a.d, a.a)),
                    StatMath.round2(StatMath.csPerMin(a.cs, a.dur)),
                    StatMath.avg(a.vision, a.games)));
        }
        roles.sort(Comparator.comparingInt(RoleStatDto::games).reversed());

        return new RolesDto(player.getGameName(), player.getTagLine(), total, roles);
    }

    private static String normalize(String teamPosition) {
        if (teamPosition == null || teamPosition.isBlank()) {
            return "NONE";
        }
        return teamPosition.toUpperCase();
    }

    private static final class Agg {
        int games;
        int wins;
        long k, d, a, cs, vision, dur;
    }
}
