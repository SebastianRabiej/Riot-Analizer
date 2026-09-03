package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.MatchEntity;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.PerfPointDto;
import com.riotanalizer.dto.PerformanceTrendDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * "Performance over time": the tracked player's current-season games as a
 * per-game metric series (oldest first), for the trend chart on the Trends page.
 * Each point keeps its role so the client can chart a metric for one position at
 * a time — mixing e.g. support and mid CS/min in a single line would be
 * misleading. Optional queue filter, consistent with the other stats endpoints.
 */
@Service
public class PerformanceService {

    private final ParticipantRepository participants;
    private final RiotProperties props;

    public PerformanceService(ParticipantRepository participants, RiotProperties props) {
        this.participants = participants;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public PerformanceTrendDto performanceTrend(Player player, Integer queueId) {
        List<Participant> parts = participants.findSeasonParticipations(
                player.getPuuid(), props.getCurrentSeasonStartEpochMs(), queueId);

        // findSeasonParticipations is newest-first; walk it backwards for oldest-first.
        List<PerfPointDto> points = new ArrayList<>(parts.size());
        for (int i = parts.size() - 1; i >= 0; i--) {
            Participant p = parts.get(i);
            MatchEntity m = p.getMatch();
            points.add(new PerfPointDto(
                    m.getGameCreation(),
                    m.getMatchId(),
                    p.getChampionId() == null ? 0 : p.getChampionId(),
                    p.getChampionName(),
                    p.getTeamPosition(),
                    p.isWin(),
                    p.getKills(),
                    p.getDeaths(),
                    p.getAssists(),
                    StatMath.round2(StatMath.kda(p.getKills(), p.getDeaths(), p.getAssists())),
                    p.getTotalCs(),
                    StatMath.round2(StatMath.csPerMin(p.getTotalCs(), m.getGameDuration())),
                    p.getVisionScore(),
                    p.getTotalDamageToChampions(),
                    p.getGoldEarned(),
                    m.getGameDuration(),
                    m.getQueueId() == null ? 0 : m.getQueueId()));
        }

        return new PerformanceTrendDto(player.getGameName(), player.getTagLine(), points.size(), points);
    }
}
