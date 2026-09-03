package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.TimeBucketDto;
import com.riotanalizer.dto.TimeStatsDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * "When do you play best" analysis: win rate by local hour-of-day, by weekday,
 * and by position within a play session. Session bucketing is what reveals tilt
 * — whether the tracked player's win rate falls the deeper into a session they go.
 * Optional champion / position filters back the Trends-tab filter bar.
 */
@Service
public class TimeStatsService {

    /** Games within this gap (ms) of the previous game's end count as one session. */
    static final long SESSION_GAP_MS = 30 * 60 * 1000L;
    /** Session positions at or beyond this are folded into one "N+" bucket. */
    static final int SESSION_CAP = 6;

    private static final String[] WEEKDAY_LABELS =
            { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };

    private final ParticipantRepository participants;
    private final RiotProperties props;

    public TimeStatsService(ParticipantRepository participants, RiotProperties props) {
        this.participants = participants;
        this.props = props;
    }

    /** Unfiltered timing (used by the Insights page). */
    @Transactional(readOnly = true)
    public TimeStatsDto timeStats(Player player, Integer queueId, int tzOffsetMinutes) {
        return timeStats(player, queueId, tzOffsetMinutes, null, null);
    }

    @Transactional(readOnly = true)
    public TimeStatsDto timeStats(Player player, Integer queueId, int tzOffsetMinutes,
                                  String champion, String position) {
        List<Participant> parts = participants.findSeasonParticipations(
                player.getPuuid(), props.getCurrentSeasonStartEpochMs(), queueId);

        String champFilter = (champion == null || champion.isBlank()) ? null : champion;
        String posFilter = (position == null || position.isBlank()) ? null : position.toUpperCase();
        if (champFilter != null || posFilter != null) {
            parts = parts.stream()
                    .filter(p -> champFilter == null || champFilter.equalsIgnoreCase(p.getChampionName()))
                    .filter(p -> posFilter == null
                            || posFilter.equals(p.getTeamPosition() == null ? "" : p.getTeamPosition().toUpperCase()))
                    .toList();
        }

        int[] hourGames = new int[24];
        int[] hourWins = new int[24];
        int[] dowGames = new int[7];
        int[] dowWins = new int[7];

        long tzMs = tzOffsetMinutes * 60_000L;
        for (Participant p : parts) {
            long localMs = p.getMatch().getGameCreation() - tzMs;
            int hour = (int) Math.floorMod(localMs / 3_600_000L, 24L);
            int dow = (int) Math.floorMod(localMs / 86_400_000L + 3, 7L); // 0=Mon .. 6=Sun
            hourGames[hour]++;
            dowGames[dow]++;
            if (p.isWin()) {
                hourWins[hour]++;
                dowWins[dow]++;
            }
        }

        List<TimeBucketDto> byHour = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            byHour.add(bucket(h, String.format("%02d:00", h), hourGames[h], hourWins[h]));
        }
        List<TimeBucketDto> byWeekday = new ArrayList<>(7);
        for (int d = 0; d < 7; d++) {
            byWeekday.add(bucket(d + 1, WEEKDAY_LABELS[d], dowGames[d], dowWins[d]));
        }

        return new TimeStatsDto(player.getGameName(), player.getTagLine(), parts.size(),
                byHour, byWeekday, bySession(parts));
    }

    /**
     * Bucket games by their position within a play session. {@code parts} is
     * newest-first; we reverse to ascending, compute session positions, then
     * aggregate wins/games per position (capping at {@link #SESSION_CAP}).
     */
    private List<TimeBucketDto> bySession(List<Participant> parts) {
        int n = parts.size();
        if (n == 0) {
            return List.of();
        }
        long[] startAsc = new long[n];
        int[] durAsc = new int[n];
        boolean[] winAsc = new boolean[n];
        for (int i = 0; i < n; i++) {
            Participant p = parts.get(n - 1 - i); // reverse to ascending
            startAsc[i] = p.getMatch().getGameCreation();
            durAsc[i] = p.getMatch().getGameDuration();
            winAsc[i] = p.isWin();
        }
        int[] pos = TrendMath.sessionPositions(startAsc, durAsc, SESSION_GAP_MS);

        int[] games = new int[SESSION_CAP + 1]; // index 1..SESSION_CAP
        int[] wins = new int[SESSION_CAP + 1];
        for (int i = 0; i < n; i++) {
            int idx = Math.min(pos[i], SESSION_CAP);
            games[idx]++;
            if (winAsc[i]) {
                wins[idx]++;
            }
        }

        List<TimeBucketDto> out = new ArrayList<>();
        for (int k = 1; k <= SESSION_CAP; k++) {
            if (games[k] == 0) {
                continue;
            }
            String label = k == SESSION_CAP ? "Game " + SESSION_CAP + "+" : "Game " + k;
            out.add(bucket(k, label, games[k], wins[k]));
        }
        return out;
    }

    private static TimeBucketDto bucket(int key, String label, int games, int wins) {
        return new TimeBucketDto(key, label, games, wins, StatMath.winRate(wins, games));
    }
}
