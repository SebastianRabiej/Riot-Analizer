package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.H2HMatchDto;
import com.riotanalizer.dto.HeadToHeadDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class HeadToHeadService {

    private final ParticipantRepository participants;
    private final RiotProperties props;

    public HeadToHeadService(ParticipantRepository participants, RiotProperties props) {
        this.participants = participants;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public HeadToHeadDto compute(Player player, Player opponent) {
        long seasonStart = props.getCurrentSeasonStartEpochMs();
        List<Participant> playerRows = participants.findSharedSeasonParticipations(
                player.getPuuid(), opponent.getPuuid(), seasonStart);

        int sameTeamGames = 0;
        int opposingGames = 0;
        int playerWinsWhenOpposing = 0;
        int opponentWinsWhenOpposing = 0;
        List<H2HMatchDto> matches = new ArrayList<>();

        for (Participant pRow : playerRows) {
            String matchId = pRow.getMatch().getMatchId();
            Participant oRow = participants.findByMatch_MatchIdAndPuuid(matchId, opponent.getPuuid())
                    .orElse(null);
            if (oRow == null) {
                continue;
            }
            boolean sameTeam = Objects.equals(pRow.getTeamId(), oRow.getTeamId());
            if (sameTeam) {
                sameTeamGames++;
            } else {
                opposingGames++;
                if (pRow.isWin()) {
                    playerWinsWhenOpposing++;
                }
                if (oRow.isWin()) {
                    opponentWinsWhenOpposing++;
                }
            }
            matches.add(new H2HMatchDto(
                    matchId,
                    pRow.getMatch().getGameCreation(),
                    sameTeam,
                    pRow.getChampionName(),
                    oRow.getChampionName(),
                    pRow.isWin(),
                    pRow.getKills(), pRow.getDeaths(), pRow.getAssists(),
                    oRow.getKills(), oRow.getDeaths(), oRow.getAssists(),
                    QueueNames.of(pRow.getMatch().getQueueId())
            ));
        }

        return new HeadToHeadDto(
                new HeadToHeadDto.PlayerRef(player.getGameName(), player.getTagLine()),
                new HeadToHeadDto.PlayerRef(opponent.getGameName(), opponent.getTagLine()),
                matches.size(),
                sameTeamGames,
                opposingGames,
                playerWinsWhenOpposing,
                opponentWinsWhenOpposing,
                matches
        );
    }
}
