package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.QueueAdviceDto;
import com.riotanalizer.dto.ReasonDto;
import com.riotanalizer.dto.TimeBucketDto;
import com.riotanalizer.dto.TimeStatsDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * "Should I queue again?" — turns the tracked player's season history into a live
 * recommendation for the next game. It blends three historical win-rate buckets
 * (position in the current session, hour of day, weekday), each shrunk toward the
 * player's overall win rate so thin buckets don't dominate, then applies streak
 * and deep-session modifiers to land on a GO / CAUTION / STOP signal. Pure math is
 * in {@link QueueAdviceMath}; here we gather the buckets and build the reasons.
 */
@Service
public class QueueAdviceService {

    /** Pseudo-count for shrinking a bucket toward the overall win rate. */
    private static final double K = 5.0;
    /** Blend weights: the session-position signal is the strongest tilt indicator. */
    private static final double W_SESSION = 0.5, W_HOUR = 0.3, W_DAY = 0.2;

    private final ParticipantRepository participants;
    private final RiotProperties props;
    private final TimeStatsService timeStatsService;

    public QueueAdviceService(ParticipantRepository participants, RiotProperties props,
                              TimeStatsService timeStatsService) {
        this.participants = participants;
        this.props = props;
        this.timeStatsService = timeStatsService;
    }

    @Transactional(readOnly = true)
    public QueueAdviceDto advice(Player player, Integer queueId, int tzOffsetMinutes) {
        List<Participant> parts = participants.findSeasonParticipations(
                player.getPuuid(), props.getCurrentSeasonStartEpochMs(), queueId);
        int total = parts.size();

        if (total == 0) {
            return new QueueAdviceDto(player.getGameName(), player.getTagLine(), "CAUTION",
                    50.0, 1, -1, 0,
                    List.of(new ReasonDto("No games this season yet — nothing to base a call on.", "neutral")));
        }

        // Overall win rate + current streak (parts is newest-first).
        int wins = 0;
        List<Boolean> winsNewestFirst = new ArrayList<>(total);
        for (Participant p : parts) {
            if (p.isWin()) {
                wins++;
            }
            winsNewestFirst.add(p.isWin());
        }
        double overall = StatMath.winRate(wins, total);
        int streak = TrendMath.currentStreak(winsNewestFirst);

        // Session position of the most recent game (build ascending arrays).
        long[] startAsc = new long[total];
        int[] durAsc = new int[total];
        for (int i = 0; i < total; i++) {
            Participant p = parts.get(total - 1 - i);
            startAsc[i] = p.getMatch().getGameCreation();
            durAsc[i] = p.getMatch().getGameDuration();
        }
        int[] pos = TrendMath.sessionPositions(startAsc, durAsc, TimeStatsService.SESSION_GAP_MS);
        int lastPos = pos[total - 1];

        Participant newest = parts.get(0);
        long lastEnd = newest.getMatch().getGameCreation()
                + Math.max(0, newest.getMatch().getGameDuration()) * 1000L;
        long now = System.currentTimeMillis();
        long minutesSince = Math.max(0, (now - lastEnd) / 60_000L);
        int nextPos = QueueAdviceMath.nextSessionPosition(lastEnd, now, lastPos, TimeStatsService.SESSION_GAP_MS);

        // Local hour / weekday for "now" (same convention as TimeStatsService).
        long localNow = now - tzOffsetMinutes * 60_000L;
        int hour = (int) Math.floorMod(localNow / 3_600_000L, 24L);
        int dow = (int) Math.floorMod(localNow / 86_400_000L + 3, 7L); // 0=Mon .. 6=Sun

        TimeStatsDto ts = timeStatsService.timeStats(player, queueId, tzOffsetMinutes);
        TimeBucketDto sessBucket = findByKey(ts.bySession(), Math.min(nextPos, TimeStatsService.SESSION_CAP));
        TimeBucketDto hourBucket = hour < ts.byHour().size() ? ts.byHour().get(hour) : null;
        TimeBucketDto dayBucket = dow < ts.byWeekday().size() ? ts.byWeekday().get(dow) : null;

        double sSess = shrunk(sessBucket, overall);
        double sHour = shrunk(hourBucket, overall);
        double sDay = shrunk(dayBucket, overall);

        double predicted = QueueAdviceMath.weightedAverage(
                new double[] { sSess, sHour, sDay },
                new double[] { W_SESSION, W_HOUR, W_DAY });

        List<ReasonDto> reasons = new ArrayList<>();

        // Lead reason: predicted vs season average.
        String lead;
        String leadTone;
        if (predicted >= overall + 2) {
            leadTone = "good";
            lead = "Right now your predicted win rate is " + pct(predicted)
                    + ", above your season average of " + pct(overall) + ".";
        } else if (predicted <= overall - 2) {
            leadTone = "bad";
            lead = "Right now your predicted win rate is " + pct(predicted)
                    + ", below your season average of " + pct(overall) + ".";
        } else {
            leadTone = "neutral";
            lead = "Your predicted win rate right now (" + pct(predicted)
                    + ") is about your season average.";
        }
        reasons.add(new ReasonDto(lead, leadTone));

        // Streak modifier.
        if (streak <= -2) {
            double pen = Math.min(8.0, (-streak - 1) * 2.0 + 1.0);
            predicted -= pen;
            reasons.add(new ReasonDto("You're on a " + (-streak) + "-game losing streak — a common tilt signal.", "bad"));
        } else if (streak >= 3) {
            predicted += 2.0;
            reasons.add(new ReasonDto("You're on a " + streak + "-game win streak — ride the momentum.", "good"));
        }

        // Deep-session modifier / note.
        if (nextPos >= 4) {
            if (sessBucket != null && sessBucket.games() >= 3 && sessBucket.winRate() <= overall - 8) {
                reasons.add(new ReasonDto("This would be game " + nextPos
                        + " of your session, and your win rate tends to fall that deep in.", "bad"));
            } else {
                reasons.add(new ReasonDto("This would be game " + nextPos + " of your session.", "neutral"));
            }
        }

        // Time-of-day / weekday positives.
        if (hourBucket != null && hourBucket.games() >= 3 && sHour >= overall + 4) {
            reasons.add(new ReasonDto("You play well around " + hourBucket.label()
                    + " (" + pct(hourBucket.winRate()) + " over " + hourBucket.games() + " games).", "good"));
        }
        if (dayBucket != null && dayBucket.games() >= 4 && sDay >= overall + 4) {
            reasons.add(new ReasonDto(dayBucket.label() + " is one of your better days ("
                    + pct(dayBucket.winRate()) + ").", "good"));
        }

        predicted = StatMath.round2(clamp(predicted, 0, 100));
        String signal = QueueAdviceMath.signal(predicted, streak, nextPos);

        return new QueueAdviceDto(player.getGameName(), player.getTagLine(), signal,
                predicted, nextPos, minutesSince, total, reasons);
    }

    private static double shrunk(TimeBucketDto b, double overall) {
        int g = b == null ? 0 : b.games();
        double wr = b == null ? overall : b.winRate();
        return QueueAdviceMath.shrink(g, wr, overall, K);
    }

    private static TimeBucketDto findByKey(List<TimeBucketDto> buckets, int key) {
        for (TimeBucketDto b : buckets) {
            if (b.key() == key) {
                return b;
            }
        }
        return null;
    }

    private static String pct(double winRate) {
        return Math.round(winRate) + "%";
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
