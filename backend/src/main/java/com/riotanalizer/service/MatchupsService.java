package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.ChampionAggRow;
import com.riotanalizer.dto.ChampionMatchupSummaryDto;
import com.riotanalizer.dto.LaneOpponentAggRow;
import com.riotanalizer.dto.LaneOpponentSummaryDto;
import com.riotanalizer.dto.TeammateAggRow;
import com.riotanalizer.dto.TeammateSummaryDto;
import com.riotanalizer.dto.OpponentAggRow;
import com.riotanalizer.dto.OpponentSummaryDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Aggregations powering the "vs player" and "vs champion" dashboards: the full
 * set of opponents / enemy champions the tracked player has faced this season,
 * with per-row records. Individual drill-downs are handled by
 * {@link HeadToHeadService} and {@link VsChampionService}.
 */
@Service
public class MatchupsService {

    private final ParticipantRepository participants;
    private final RiotProperties props;

    public MatchupsService(ParticipantRepository participants, RiotProperties props) {
        this.participants = participants;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<OpponentSummaryDto> opponents(Player player, int minGames, int limit) {
        long seasonStart = props.getCurrentSeasonStartEpochMs();
        long mg = Math.max(1, minGames);
        List<OpponentAggRow> rows =
                participants.aggregateOpponents(player.getPuuid(), seasonStart, mg);
        return rows.stream()
                .limit(limit <= 0 ? Long.MAX_VALUE : limit)
                .map(r -> {
                    int games = r.games() == null ? 0 : r.games().intValue();
                    int wins = r.wins() == null ? 0 : r.wins().intValue();
                    int losses = games - wins;
                    return new OpponentSummaryDto(
                            r.puuid(), r.gameName(), r.tagLine(),
                            games, wins, losses, StatMath.winRate(wins, games));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChampionMatchupSummaryDto> championMatchups(Player player, int limit) {
        long seasonStart = props.getCurrentSeasonStartEpochMs();
        List<ChampionAggRow> rows =
                participants.aggregateChampionMatchups(player.getPuuid(), seasonStart);
        return rows.stream()
                .limit(limit <= 0 ? Long.MAX_VALUE : limit)
                .map(r -> {
                    int games = r.games() == null ? 0 : r.games().intValue();
                    int wins = r.wins() == null ? 0 : r.wins().intValue();
                    int losses = games - wins;
                    long k = r.kills() == null ? 0 : r.kills();
                    long d = r.deaths() == null ? 0 : r.deaths();
                    long a = r.assists() == null ? 0 : r.assists();
                    return new ChampionMatchupSummaryDto(
                            r.championId(), r.championName(),
                            games, wins, losses, StatMath.winRate(wins, games),
                            StatMath.avg(k, games), StatMath.avg(d, games), StatMath.avg(a, games),
                            StatMath.round2(StatMath.kda(k, d, a)));
                })
                .toList();
    }
    @Transactional(readOnly = true)
    public List<TeammateSummaryDto> teammates(Player player, int minGames, int limit) {
        long seasonStart = props.getCurrentSeasonStartEpochMs();
        long mg = Math.max(1, minGames);
        List<TeammateAggRow> rows =
                participants.aggregateTeammates(player.getPuuid(), seasonStart, mg);
        return rows.stream()
                .limit(limit <= 0 ? Long.MAX_VALUE : limit)
                .map(r -> {
                    int games = r.games() == null ? 0 : r.games().intValue();
                    int wins = r.wins() == null ? 0 : r.wins().intValue();
                    int losses = games - wins;
                    return new TeammateSummaryDto(
                            r.puuid(), r.gameName(), r.tagLine(),
                            games, wins, losses, StatMath.winRate(wins, games));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LaneOpponentSummaryDto> laneOpponents(Player player, int limit) {
        long seasonStart = props.getCurrentSeasonStartEpochMs();
        List<LaneOpponentAggRow> rows =
                participants.aggregateLaneOpponents(player.getPuuid(), seasonStart);
        return rows.stream()
                .limit(limit <= 0 ? Long.MAX_VALUE : limit)
                .map(r -> {
                    int games = r.games() == null ? 0 : r.games().intValue();
                    int wins = r.wins() == null ? 0 : r.wins().intValue();
                    int losses = games - wins;
                    long k = r.kills() == null ? 0 : r.kills();
                    long d = r.deaths() == null ? 0 : r.deaths();
                    long a = r.assists() == null ? 0 : r.assists();
                    return new LaneOpponentSummaryDto(
                            r.championId(), r.championName(),
                            games, wins, losses, StatMath.winRate(wins, games),
                            StatMath.avg(k, games), StatMath.avg(d, games), StatMath.avg(a, games),
                            StatMath.round2(StatMath.kda(k, d, a)));
                })
                .toList();
    }
}
