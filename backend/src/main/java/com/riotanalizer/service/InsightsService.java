package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.Participant;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.ChampionMatchupSummaryDto;
import com.riotanalizer.dto.InsightDto;
import com.riotanalizer.dto.InsightsDto;
import com.riotanalizer.dto.OpponentSummaryDto;
import com.riotanalizer.dto.RoleStatDto;
import com.riotanalizer.dto.RolesDto;
import com.riotanalizer.dto.TeammateSummaryDto;
import com.riotanalizer.dto.TimeBucketDto;
import com.riotanalizer.dto.TimeStatsDto;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Turns the raw aggregations into a ranked list of plain-language findings for
 * the Insights view: current form, nemesis, best duo, bogey champion, strongest
 * role, tilt, and best time to queue. Every finding carries a drill-down link so
 * a click opens the detailed view behind it.
 */
@Service
public class InsightsService {

    /** Minimum games before a win-rate extreme is trustworthy enough to surface. */
    private static final int MIN_GAMES = 3;

    private final ParticipantRepository participants;
    private final MatchupsService matchups;
    private final RolesService rolesService;
    private final TimeStatsService timeStatsService;
    private final RiotProperties props;

    public InsightsService(ParticipantRepository participants, MatchupsService matchups,
                           RolesService rolesService, TimeStatsService timeStatsService,
                           RiotProperties props) {
        this.participants = participants;
        this.matchups = matchups;
        this.rolesService = rolesService;
        this.timeStatsService = timeStatsService;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public InsightsDto compute(Player player, Integer queueId, int tzOffsetMinutes) {
        List<Participant> parts = participants.findSeasonParticipations(
                player.getPuuid(), props.getCurrentSeasonStartEpochMs(), queueId);

        List<Boolean> wins = new ArrayList<>(parts.size());
        for (Participant p : parts) {
            wins.add(p.isWin());
        }
        int currentStreak = TrendMath.currentStreak(wins);
        int longestWin = TrendMath.longestStreak(wins, true);
        int longestLoss = TrendMath.longestStreak(wins, false);

        List<InsightDto> highlights = new ArrayList<>();

        streakInsight(currentStreak).ifPresent(highlights::add);
        nemesisAndVictim(player, highlights);
        duoInsights(player, highlights);
        championInsights(player, highlights);
        roleInsight(player, queueId, highlights);
        tiltAndTimeInsights(player, queueId, tzOffsetMinutes, highlights);

        return new InsightsDto(player.getGameName(), player.getTagLine(), parts.size(),
                currentStreak, longestWin, longestLoss, highlights);
    }

    // ---- individual findings ----

    private java.util.Optional<InsightDto> streakInsight(int streak) {
        if (streak >= 3) {
            return java.util.Optional.of(new InsightDto("streak", "🔥",
                    streak + "-game win streak",
                    "You're on a roll — " + streak + " wins in a row right now.",
                    "good", null, null));
        }
        if (streak <= -3) {
            int n = -streak;
            return java.util.Optional.of(new InsightDto("streak", "🧊",
                    n + "-game losing streak",
                    "You've dropped your last " + n + " games. A break might reset the tilt.",
                    "bad", "trends", "time"));
        }
        return java.util.Optional.empty();
    }

    private void nemesisAndVictim(Player player, List<InsightDto> out) {
        List<OpponentSummaryDto> opps = matchups.opponents(player, MIN_GAMES, 0);
        if (opps.isEmpty()) {
            return;
        }
        OpponentSummaryDto nemesis = opps.stream()
                .min(Comparator.comparingDouble(OpponentSummaryDto::winRate)
                        .thenComparing(Comparator.comparingInt(OpponentSummaryDto::games).reversed()))
                .orElse(null);
        if (nemesis != null && nemesis.winRate() < 50.0 && named(nemesis.gameName(), nemesis.tagLine())) {
            String id = riotId(nemesis.gameName(), nemesis.tagLine());
            out.add(new InsightDto("nemesis", "👹",
                    "Nemesis: " + id,
                    "You're " + nemesis.wins() + "-" + nemesis.losses() + " (" + pct(nemesis.winRate())
                            + ") when " + id + " is on the enemy team, across " + nemesis.games() + " games.",
                    "bad", "vs-player", id));
        }
        OpponentSummaryDto victim = opps.stream()
                .max(Comparator.comparingDouble(OpponentSummaryDto::winRate)
                        .thenComparing(OpponentSummaryDto::games))
                .orElse(null);
        if (victim != null && victim.winRate() > 55.0 && named(victim.gameName(), victim.tagLine())) {
            String id = riotId(victim.gameName(), victim.tagLine());
            out.add(new InsightDto("favourite-opponent", "🎯",
                    "You own " + id,
                    "You beat " + id + " " + pct(victim.winRate()) + " of the time ("
                            + victim.wins() + "-" + victim.losses() + " across " + victim.games() + " games).",
                    "good", "vs-player", id));
        }
    }

    private void duoInsights(Player player, List<InsightDto> out) {
        List<TeammateSummaryDto> mates = matchups.teammates(player, MIN_GAMES, 0);
        if (mates.isEmpty()) {
            return;
        }
        TeammateSummaryDto best = mates.stream()
                .max(Comparator.comparingDouble(TeammateSummaryDto::winRate)
                        .thenComparing(TeammateSummaryDto::games))
                .orElse(null);
        if (best != null && best.winRate() > 55.0 && named(best.gameName(), best.tagLine())) {
            String id = riotId(best.gameName(), best.tagLine());
            out.add(new InsightDto("best-duo", "🤝",
                    "Best duo: " + id,
                    "With " + id + " on your team you win " + pct(best.winRate()) + " ("
                            + best.wins() + "-" + best.losses() + " over " + best.games() + " games).",
                    "good", "with-player", id));
        }
        TeammateSummaryDto worst = mates.stream()
                .min(Comparator.comparingDouble(TeammateSummaryDto::winRate)
                        .thenComparing(Comparator.comparingInt(TeammateSummaryDto::games).reversed()))
                .orElse(null);
        if (worst != null && worst.games() >= 4 && worst.winRate() < 40.0
                && named(worst.gameName(), worst.tagLine())) {
            String id = riotId(worst.gameName(), worst.tagLine());
            out.add(new InsightDto("worst-duo", "🔗",
                    "Anchor: " + id,
                    "Games with " + id + " on your team go " + worst.wins() + "-" + worst.losses()
                            + " (" + pct(worst.winRate()) + " over " + worst.games() + " games).",
                    "bad", "with-player", id));
        }
    }

    private void championInsights(Player player, List<InsightDto> out) {
        List<ChampionMatchupSummaryDto> champs = matchups.championMatchups(player, 0);
        List<ChampionMatchupSummaryDto> eligible = champs.stream()
                .filter(c -> c.games() >= MIN_GAMES && c.championName() != null)
                .toList();
        if (eligible.isEmpty()) {
            return;
        }
        ChampionMatchupSummaryDto bogey = eligible.stream()
                .min(Comparator.comparingDouble(ChampionMatchupSummaryDto::winRate)
                        .thenComparing(Comparator.comparingInt(ChampionMatchupSummaryDto::games).reversed()))
                .orElse(null);
        if (bogey != null && bogey.winRate() < 45.0) {
            out.add(new InsightDto("bogey-champion", "☠️",
                    "Bogey pick: " + bogey.championName(),
                    "When the enemy has " + bogey.championName() + " you win just " + pct(bogey.winRate())
                            + " (" + bogey.wins() + "-" + bogey.losses() + " over " + bogey.games() + " games).",
                    "bad", "vs-champion", bogey.championName()));
        }
        ChampionMatchupSummaryDto comfort = eligible.stream()
                .max(Comparator.comparingDouble(ChampionMatchupSummaryDto::winRate)
                        .thenComparing(ChampionMatchupSummaryDto::games))
                .orElse(null);
        if (comfort != null && comfort.winRate() > 60.0) {
            out.add(new InsightDto("comfort-matchup", "😎",
                    "Free lane vs " + comfort.championName(),
                    "You beat teams with " + comfort.championName() + " " + pct(comfort.winRate())
                            + " of the time (" + comfort.wins() + "-" + comfort.losses()
                            + " over " + comfort.games() + " games).",
                    "good", "vs-champion", comfort.championName()));
        }
    }

    private void roleInsight(Player player, Integer queueId, List<InsightDto> out) {
        RolesDto roles = rolesService.roles(player, queueId);
        List<RoleStatDto> eligible = roles.roles().stream()
                .filter(r -> r.games() >= MIN_GAMES && !"NONE".equals(r.position()))
                .toList();
        if (eligible.size() < 2) {
            return; // need at least two real roles to make a comparison meaningful
        }
        RoleStatDto best = eligible.stream()
                .max(Comparator.comparingDouble(RoleStatDto::winRate)
                        .thenComparing(RoleStatDto::games))
                .orElse(null);
        if (best != null) {
            out.add(new InsightDto("best-role", "🧭",
                    "Strongest role: " + prettyRole(best.position()),
                    "You win " + pct(best.winRate()) + " in " + prettyRole(best.position())
                            + " (" + best.wins() + "-" + best.losses() + " over " + best.games()
                            + " games) — your best lane this season.",
                    "good", "trends", "roles"));
        }
    }

    private void tiltAndTimeInsights(Player player, Integer queueId, int tz, List<InsightDto> out) {
        TimeStatsDto ts = timeStatsService.timeStats(player, queueId, tz);

        // Tilt: compare first two games of a session vs the third onward.
        int earlyG = 0, earlyW = 0, lateG = 0, lateW = 0;
        for (TimeBucketDto b : ts.bySession()) {
            if (b.key() <= 2) {
                earlyG += b.games();
                earlyW += b.wins();
            } else {
                lateG += b.games();
                lateW += b.wins();
            }
        }
        if (earlyG >= 5 && lateG >= 5) {
            double earlyWr = StatMath.winRate(earlyW, earlyG);
            double lateWr = StatMath.winRate(lateW, lateG);
            if (earlyWr - lateWr >= 12.0) {
                out.add(new InsightDto("tilt", "📉",
                        "You tilt in long sessions",
                        "First two games of a session: " + pct(earlyWr) + " win rate. From the third on: "
                                + pct(lateWr) + ". Consider stopping after a couple.",
                        "bad", "trends", "time"));
            } else if (lateWr >= earlyWr) {
                out.add(new InsightDto("stamina", "🔋",
                        "You hold up in long sessions",
                        "Your win rate doesn't drop as a session goes on (" + pct(earlyWr)
                                + " early vs " + pct(lateWr) + " later).",
                        "good", "trends", "time"));
            }
        }

        // Best weekday (need a little volume to be meaningful).
        TimeBucketDto bestDay = ts.byWeekday().stream()
                .filter(b -> b.games() >= 4)
                .max(Comparator.comparingDouble(TimeBucketDto::winRate)
                        .thenComparing(TimeBucketDto::games))
                .orElse(null);
        if (bestDay != null && bestDay.winRate() > 55.0) {
            out.add(new InsightDto("best-day", "📅",
                    bestDay.label() + " is your day",
                    "You win " + pct(bestDay.winRate()) + " of games on " + bestDay.label()
                            + " (" + bestDay.games() + " games).",
                    "good", "trends", "time"));
        }
    }

    // ---- helpers ----

    private static boolean named(String gameName, String tagLine) {
        return gameName != null && !gameName.isBlank() && tagLine != null && !tagLine.isBlank();
    }

    private static String riotId(String gameName, String tagLine) {
        return gameName + "#" + tagLine;
    }

    private static String pct(double winRate) {
        return Math.round(winRate) + "%";
    }

    private static String prettyRole(String position) {
        return switch (position == null ? "" : position.toUpperCase(Locale.ROOT)) {
            case "TOP" -> "Top";
            case "JUNGLE" -> "Jungle";
            case "MIDDLE", "MID" -> "Mid";
            case "BOTTOM", "BOT" -> "Bot";
            case "UTILITY", "SUPPORT" -> "Support";
            default -> position;
        };
    }
}
