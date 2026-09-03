package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.DurationBucketDto;
import com.riotanalizer.dto.GameLengthStatsDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * "Win rate by game length": buckets the tracked player's current-season games by
 * duration and reports the record in each, plus average win vs loss length. Pure
 * aggregation over stored match durations (no timeline). Optional queue, champion
 * and position filters mirror the other Trends-tab endpoints; filtering is done in
 * Java, consistent with {@link RolesService} / {@link TimeStatsService}.
 */
@Service
public class GameLengthService {

    /** Upper edges (seconds) of the fixed buckets; the last bucket is open-ended. */
    private static final int[] EDGES = { 20 * 60, 25 * 60, 30 * 60, 35 * 60 };
    private static final String[] LABELS = { "<20", "20–25", "25–30", "30–35", "35+" };

    private final ParticipantRepository participants;
    private final RiotProperties props;

    public GameLengthService(ParticipantRepository participants, RiotProperties props) {
        this.participants = participants;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public GameLengthStatsDto gameLength(Player player, Integer queueId, String champion, String position) {
        List<Participant> parts = participants.findSeasonParticipations(
                player.getPuuid(), props.getCurrentSeasonStartEpochMs(), queueId);

        String champFilter = (champion == null || champion.isBlank()) ? null : champion;
        String posFilter = (position == null || position.isBlank()) ? null : position.toUpperCase();

        int n = EDGES.length + 1; // number of buckets
        int[] games = new int[n];
        int[] wins = new int[n];

        long winDurSum = 0, lossDurSum = 0;
        int winGames = 0, lossGames = 0;
        int total = 0;

        for (Participant p : parts) {
            if (champFilter != null && !champFilter.equalsIgnoreCase(p.getChampionName())) {
                continue;
            }
            if (posFilter != null) {
                String pos = p.getTeamPosition() == null ? "" : p.getTeamPosition().toUpperCase();
                if (!posFilter.equals(pos)) {
                    continue;
                }
            }
            total++;
            int dur = p.getMatch().getGameDuration();
            int idx = bucketIndex(dur);
            games[idx]++;
            if (p.isWin()) {
                wins[idx]++;
                winDurSum += dur;
                winGames++;
            } else {
                lossDurSum += dur;
                lossGames++;
            }
        }

        List<DurationBucketDto> buckets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int minSec = i == 0 ? 0 : EDGES[i - 1];
            int maxSec = i < EDGES.length ? EDGES[i] : Integer.MAX_VALUE;
            buckets.add(new DurationBucketDto(
                    i + 1, LABELS[i], minSec, maxSec, games[i], wins[i],
                    StatMath.winRate(wins[i], games[i])));
        }

        return new GameLengthStatsDto(
                player.getGameName(), player.getTagLine(), total, buckets,
                StatMath.avg(winDurSum, winGames),
                StatMath.avg(lossDurSum, lossGames));
    }

    private static int bucketIndex(int durationSec) {
        for (int i = 0; i < EDGES.length; i++) {
            if (durationSec < EDGES[i]) {
                return i;
            }
        }
        return EDGES.length; // open-ended top bucket
    }
}
