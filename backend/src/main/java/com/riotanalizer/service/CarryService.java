package com.riotanalizer.service;

import com.riotanalizer.config.RiotProperties;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.CarryIndexDto;
import com.riotanalizer.dto.CarryPointDto;
import com.riotanalizer.dto.CarryRow;
import com.riotanalizer.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * "Carry Index": how much of your team's output is you, and does carrying win?
 * Built entirely from the stored ten participants per match — no timeline data.
 *
 * Per game we take the player's share of their team's damage, gold and kill
 * participation (team totals include the player) and blend them into a 0..100
 * carry score where an even 1/5 contribution (0.20 share) lands at ~50 and a
 * dominant 0.40 share tops out at 100. The season Carry Index is the mean of the
 * per-game scores. We also track how often the player is their team's top damage
 * dealer and whether those games are won more often than the rest.
 */
@Service
public class CarryService {

    /** Weight on damage share in the per-game blend (gold + KP split the rest). */
    private static final double W_DAMAGE = 0.50;
    private static final double W_GOLD = 0.25;
    private static final double W_KP = 0.25;
    /** Blended share that maps to a Carry Index of 100 (0.20 = even split -> 50). */
    private static final double FULL_CARRY_SHARE = 0.40;

    private final ParticipantRepository participants;
    private final RiotProperties props;

    public CarryService(ParticipantRepository participants, RiotProperties props) {
        this.participants = participants;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public CarryIndexDto carry(Player player, Integer queueId, String champion, String position) {
        String champFilter = (champion == null || champion.isBlank()) ? null : champion.toLowerCase();
        String posFilter = (position == null || position.isBlank()) ? null : position.toUpperCase();

        List<CarryRow> rows = participants.aggregateCarry(
                player.getPuuid(), props.getCurrentSeasonStartEpochMs(), queueId, champFilter, posFilter);

        List<CarryPointDto> perGame = new ArrayList<>(rows.size());
        double sumDmgShare = 0, sumGoldShare = 0, sumKp = 0, sumScore = 0;
        int topGames = 0, topWins = 0, otherGames = 0, otherWins = 0;

        for (CarryRow r : rows) {
            long teamDmg = nz(r.teamDamage());
            long teamGold = nz(r.teamGold());
            long teamKills = nz(r.teamKills());
            long teamMaxDmg = nz(r.teamMaxDamage());

            double dmgShare = StatMath.share(r.myDamage(), teamDmg);   // 0..100
            double goldShare = StatMath.share(r.myGold(), teamGold);   // 0..100
            double kp = StatMath.share(r.myKills() + r.myAssists(), teamKills); // 0..100

            double blendFraction = (W_DAMAGE * dmgShare + W_GOLD * goldShare + W_KP * kp) / 100.0;
            double score = clamp01(blendFraction / FULL_CARRY_SHARE) * 100.0;
            score = StatMath.round2(score);

            boolean top = teamMaxDmg > 0 && r.myDamage() >= teamMaxDmg;

            sumDmgShare += dmgShare;
            sumGoldShare += goldShare;
            sumKp += kp;
            sumScore += score;
            if (top) {
                topGames++;
                if (r.win()) {
                    topWins++;
                }
            } else {
                otherGames++;
                if (r.win()) {
                    otherWins++;
                }
            }

            perGame.add(new CarryPointDto(
                    r.matchId(), r.gameCreation(), r.championName(), r.teamPosition(), r.win(),
                    dmgShare, goldShare, kp, top, score));
        }

        int games = rows.size();
        double avgDmgShare = mean(sumDmgShare, games);
        double avgGoldShare = mean(sumGoldShare, games);
        double avgKp = mean(sumKp, games);
        double carryIndex = mean(sumScore, games);
        double topRate = StatMath.share(topGames, games);

        // perGame comes back newest-first (query order); reverse to oldest-first so
        // the client can plot it as a left-to-right time series like the other charts.
        List<CarryPointDto> oldestFirst = new ArrayList<>(perGame);
        java.util.Collections.reverse(oldestFirst);

        return new CarryIndexDto(
                player.getGameName(), player.getTagLine(), games,
                avgDmgShare, avgGoldShare, avgKp, topRate,
                StatMath.winRate(topWins, topGames),
                StatMath.winRate(otherWins, otherGames),
                carryIndex, oldestFirst);
    }

    private static double mean(double sum, int count) {
        return count <= 0 ? 0.0 : StatMath.round2(sum / count);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static long nz(Integer v) {
        return v == null ? 0L : v;
    }
}
