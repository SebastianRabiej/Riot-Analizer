package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.MatchEntity;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.ChampionStatDto;
import com.riotanalizer.dto.MatchSummaryDto;
import com.riotanalizer.dto.PlayerStatsDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private final ParticipantRepository participants;
    private final RiotProperties props;

    public StatsService(ParticipantRepository participants, RiotProperties props) {
        this.participants = participants;
        this.props = props;
    }

    private long seasonStart() {
        return props.getCurrentSeasonStartEpochMs();
    }

    @Transactional(readOnly = true)
    public PlayerStatsDto playerStats(Player player, Integer queueId) {
        List<Participant> parts = participants.findSeasonParticipations(player.getPuuid(), seasonStart(), queueId);

        int games = parts.size();
        int wins = 0;
        long sumK = 0, sumD = 0, sumA = 0, sumCs = 0, sumVision = 0, sumDmg = 0, sumGold = 0, sumDur = 0;

        for (Participant p : parts) {
            if (p.isWin()) {
                wins++;
            }
            sumK += p.getKills();
            sumD += p.getDeaths();
            sumA += p.getAssists();
            sumCs += p.getTotalCs();
            sumVision += p.getVisionScore();
            sumDmg += p.getTotalDamageToChampions();
            sumGold += p.getGoldEarned();
            sumDur += p.getMatch().getGameDuration();
        }
        int losses = games - wins;

        // recentForm: most recent 20 (list already ordered by gameCreation desc)
        List<Boolean> recentForm = new ArrayList<>();
        for (int i = 0; i < parts.size() && i < 20; i++) {
            recentForm.add(parts.get(i).isWin());
        }

        return new PlayerStatsDto(
                player.getGameName(),
                player.getTagLine(),
                games,
                wins,
                losses,
                StatMath.winRate(wins, games),
                StatMath.avg(sumK, games),
                StatMath.avg(sumD, games),
                StatMath.avg(sumA, games),
                StatMath.round2(StatMath.kda(sumK, sumD, sumA)),
                StatMath.round2(StatMath.csPerMin(sumCs, sumDur)),
                StatMath.avg(sumVision, games),
                StatMath.avg(sumDmg, games),
                StatMath.avg(sumGold, games),
                StatMath.avg(sumDur, games),
                recentForm,
                championStats(parts)
        );
    }

    private List<ChampionStatDto> championStats(List<Participant> parts) {
        Map<Integer, Agg> byChamp = new LinkedHashMap<>();
        for (Participant p : parts) {
            int champId = p.getChampionId() == null ? -1 : p.getChampionId();
            Agg a = byChamp.computeIfAbsent(champId, k -> new Agg());
            a.championId = champId;
            a.championName = p.getChampionName();
            a.games++;
            if (p.isWin()) {
                a.wins++;
            }
            a.k += p.getKills();
            a.d += p.getDeaths();
            a.a += p.getAssists();
            a.cs += p.getTotalCs();
            a.dur += p.getMatch().getGameDuration();
        }
        List<ChampionStatDto> out = new ArrayList<>();
        for (Agg a : byChamp.values()) {
            out.add(new ChampionStatDto(
                    a.championId,
                    a.championName,
                    a.games,
                    a.wins,
                    a.games - a.wins,
                    StatMath.winRate(a.wins, a.games),
                    StatMath.avg(a.k, a.games),
                    StatMath.avg(a.d, a.games),
                    StatMath.avg(a.a, a.games),
                    StatMath.round2(StatMath.kda(a.k, a.d, a.a)),
                    StatMath.round2(StatMath.csPerMin(a.cs, a.dur))
            ));
        }
        out.sort(Comparator.comparingInt(ChampionStatDto::games).reversed());
        return out;
    }

    @Transactional(readOnly = true)
    public Page<MatchSummaryDto> matches(Player player, int page, int size, Integer queueId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Participant> parts = participants.findSeasonParticipationsPage(
                player.getPuuid(), seasonStart(), queueId, pageable);
        return parts.map(this::toSummary);
    }

    private MatchSummaryDto toSummary(Participant p) {
        MatchEntity m = p.getMatch();
        return new MatchSummaryDto(
                m.getMatchId(),
                m.getQueueId() == null ? 0 : m.getQueueId(),
                QueueNames.of(m.getQueueId()),
                m.getGameCreation(),
                m.getGameDuration(),
                p.getChampionId() == null ? 0 : p.getChampionId(),
                p.getChampionName(),
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
                p.getTeamPosition(),
                p.itemsArray(),
                p.getSummoner1Id() == null ? 0 : p.getSummoner1Id(),
                p.getSummoner2Id() == null ? 0 : p.getSummoner2Id()
        );
    }

    private static final class Agg {
        int championId;
        String championName;
        int games;
        int wins;
        long k, d, a, cs, dur;
    }
}
