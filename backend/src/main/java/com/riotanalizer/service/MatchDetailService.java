package com.riotanalizer.service;

import com.riotanalizer.domain.MatchEntity;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.dto.MatchDetailDto;
import com.riotanalizer.dto.MatchParticipantDto;
import com.riotanalizer.dto.MatchTeamDto;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class MatchDetailService {

    private static final Map<String, Integer> ROLE_ORDER = Map.of(
            "TOP", 0, "JUNGLE", 1, "MIDDLE", 2, "BOTTOM", 3, "UTILITY", 4);

    private final MatchRepository matches;

    public MatchDetailService(MatchRepository matches) {
        this.matches = matches;
    }

    @Transactional(readOnly = true)
    public MatchDetailDto detail(String matchId) {
        MatchEntity m = matches.findWithParticipants(matchId)
                .orElseThrow(() -> new NotFoundException("Match not found: " + matchId));

        List<MatchTeamDto> teams = new ArrayList<>();
        for (int teamId : new int[] {100, 200}) {
            List<Participant> members = new ArrayList<>();
            for (Participant p : m.getParticipants()) {
                if (p.getTeamId() != null && p.getTeamId() == teamId) {
                    members.add(p);
                }
            }
            if (members.isEmpty()) {
                continue;
            }
            members.sort(Comparator.comparingInt(p -> roleRank(p.getTeamPosition())));

            boolean win = members.get(0).isWin();
            int k = 0, d = 0, a = 0, gold = 0, dmg = 0;
            List<MatchParticipantDto> parts = new ArrayList<>();
            for (Participant p : members) {
                k += p.getKills();
                d += p.getDeaths();
                a += p.getAssists();
                gold += p.getGoldEarned();
                dmg += p.getTotalDamageToChampions();
                parts.add(toParticipant(p, m.getGameDuration()));
            }
            teams.add(new MatchTeamDto(teamId, win, k, d, a, gold, dmg, parts));
        }

        return new MatchDetailDto(
                m.getMatchId(),
                m.getQueueId() == null ? 0 : m.getQueueId(),
                QueueNames.of(m.getQueueId()),
                m.getGameCreation(),
                m.getGameDuration(),
                m.getMapId(),
                m.getGameVersion(),
                teams
        );
    }

    private MatchParticipantDto toParticipant(Participant p, int gameDuration) {
        return new MatchParticipantDto(
                p.getPuuid(),
                p.getRiotIdGameName(),
                p.getRiotIdTagLine(),
                p.getChampionId() == null ? 0 : p.getChampionId(),
                p.getChampionName(),
                p.getTeamId() == null ? 0 : p.getTeamId(),
                p.getTeamPosition(),
                p.isWin(),
                p.getKills(),
                p.getDeaths(),
                p.getAssists(),
                StatMath.round2(StatMath.kda(p.getKills(), p.getDeaths(), p.getAssists())),
                p.getTotalCs(),
                StatMath.round2(StatMath.csPerMin(p.getTotalCs(), gameDuration)),
                p.getVisionScore(),
                p.getTotalDamageToChampions(),
                p.getGoldEarned(),
                p.itemsArray(),
                p.getSummoner1Id() == null ? 0 : p.getSummoner1Id(),
                p.getSummoner2Id() == null ? 0 : p.getSummoner2Id()
        );
    }

    private static int roleRank(String position) {
        if (position == null) {
            return 9;
        }
        return ROLE_ORDER.getOrDefault(position.toUpperCase(), 9);
    }
}
