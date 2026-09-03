package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.VsChampionDto;
import com.riotanalizer.dto.VsChampionMatchDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class VsChampionService {

    private final ParticipantRepository participants;
    private final RiotProperties props;

    public VsChampionService(ParticipantRepository participants, RiotProperties props) {
        this.participants = participants;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public VsChampionDto compute(Player player, String championName) {
        long seasonStart = props.getCurrentSeasonStartEpochMs();
        List<Participant> rows = participants.findSeasonParticipationsVsChampion(
                player.getPuuid(), championName, seasonStart);

        int wins = 0;
        long sumK = 0, sumD = 0, sumA = 0;
        List<VsChampionMatchDto> matches = new ArrayList<>();

        for (Participant p : rows) {
            if (p.isWin()) {
                wins++;
            }
            sumK += p.getKills();
            sumD += p.getDeaths();
            sumA += p.getAssists();
            matches.add(new VsChampionMatchDto(
                    p.getMatch().getMatchId(),
                    p.getMatch().getGameCreation(),
                    p.getChampionName(),
                    p.isWin(),
                    p.getKills(), p.getDeaths(), p.getAssists(),
                    QueueNames.of(p.getMatch().getQueueId())
            ));
        }

        int games = rows.size();
        int losses = games - wins;

        return new VsChampionDto(
                player.getGameName(),
                player.getTagLine(),
                championName,
                games,
                wins,
                losses,
                StatMath.winRate(wins, games),
                StatMath.avg(sumK, games),
                StatMath.avg(sumD, games),
                StatMath.avg(sumA, games),
                StatMath.round2(StatMath.kda(sumK, sumD, sumA)),
                matches
        );
    }
}
