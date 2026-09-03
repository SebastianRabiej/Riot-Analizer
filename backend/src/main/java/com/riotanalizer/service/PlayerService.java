package com.riotanalizer.service;

import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.client.dto.AccountDto;
import com.riotanalizer.client.dto.LeagueEntryDto;
import com.riotanalizer.client.dto.SummonerDto;
import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.PlayerDto;
import com.riotanalizer.dto.RankDto;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PlayerService {

    private static final String SOLO_QUEUE = "RANKED_SOLO_5x5";
    private static final String FLEX_QUEUE = "RANKED_FLEX_SR";

    private final RiotApiClient riot;
    private final PlayerRepository players;
    private final FetchService fetchService;

    public PlayerService(RiotApiClient riot, PlayerRepository players, FetchService fetchService) {
        this.riot = riot;
        this.players = players;
        this.fetchService = fetchService;
    }

    /**
     * Return a tracked player, resolving+registering via Riot if unknown.
     */
    @Transactional
    public Player getOrRegister(String gameName, String tagLine) {
        return players.findByGameNameIgnoreCaseAndTagLineIgnoreCase(gameName, tagLine)
                .orElseGet(() -> register(gameName, tagLine));
    }

    /**
     * Look up an already-known player only (no Riot resolution). Throws if unknown.
     */
    @Transactional(readOnly = true)
    public Player requireKnown(String gameName, String tagLine) {
        return players.findByGameNameIgnoreCaseAndTagLineIgnoreCase(gameName, tagLine)
                .orElseThrow(() -> new NotFoundException("Player not tracked: " + gameName + "#" + tagLine));
    }

    @Transactional
    public Player register(String gameName, String tagLine) {
        AccountDto account = riot.getAccountByRiotId(gameName, tagLine);
        Player player = players.findById(account.puuid()).orElseGet(Player::new);
        player.setPuuid(account.puuid());
        player.setGameName(account.gameName() != null ? account.gameName() : gameName);
        player.setTagLine(account.tagLine() != null ? account.tagLine() : tagLine);
        // Registration no longer implies tracking; that is now an explicit
        // choice (see track()/untrack()). A brand-new Player defaults to
        // tracked=false, and an existing one keeps whatever flag it had.
        refreshProfileFields(player);
        return players.save(player);
    }

    /**
     * Refresh summoner + rank info from Riot and persist.
     */
    @Transactional
    public Player refreshProfile(Player player) {
        refreshProfileFields(player);
        return players.save(player);
    }

    private void refreshProfileFields(Player player) {
        SummonerDto summoner = riot.getSummonerByPuuid(player.getPuuid());
        player.setSummonerId(summoner.id());
        player.setProfileIconId(summoner.profileIconId());
        if (summoner.summonerLevel() != null) {
            player.setSummonerLevel(summoner.summonerLevel().intValue());
        }

        // reset rank fields, then re-apply
        player.setSoloTier(null);
        player.setSoloRank(null);
        player.setSoloLeaguePoints(null);
        player.setSoloWins(null);
        player.setSoloLosses(null);
        player.setFlexTier(null);
        player.setFlexRank(null);
        player.setFlexLeaguePoints(null);
        player.setFlexWins(null);
        player.setFlexLosses(null);

        for (LeagueEntryDto entry : riot.getLeagueEntriesByPuuid(player.getPuuid())) {
            if (SOLO_QUEUE.equals(entry.queueType())) {
                player.setSoloTier(entry.tier());
                player.setSoloRank(entry.rank());
                player.setSoloLeaguePoints(entry.leaguePoints());
                player.setSoloWins(entry.wins());
                player.setSoloLosses(entry.losses());
            } else if (FLEX_QUEUE.equals(entry.queueType())) {
                player.setFlexTier(entry.tier());
                player.setFlexRank(entry.rank());
                player.setFlexLeaguePoints(entry.leaguePoints());
                player.setFlexWins(entry.wins());
                player.setFlexLosses(entry.losses());
            }
        }
        player.setLastFetched(Instant.now());
    }

    /**
     * Manual refresh: profile + full current-season match backfill.
     *
     * <p>Deliberately NOT {@code @Transactional}: the backfill can page through
     * hundreds of rate-limited Riot calls, and wrapping that in a single
     * transaction would pin a DB connection for the whole time. Profile refresh
     * and each match are persisted in their own transactions instead.
     */
    public Player refresh(String gameName, String tagLine) {
        Player player = getOrRegister(gameName, tagLine);
        refreshProfile(player);
        fetchService.fetchAndStore(player);
        return player;
    }

    /**
     * Add a player to the tracked set: resolve/register them, flip the tracked
     * flag, refresh their profile and backfill their current-season matches so
     * there is data to show immediately. Like {@link #refresh}, deliberately not
     * {@code @Transactional} because the backfill can page through many
     * rate-limited Riot calls.
     */
    public Player track(String gameName, String tagLine) {
        Player player = getOrRegister(gameName, tagLine);
        player.setTracked(true);
        refreshProfile(player);
        fetchService.fetchAndStore(player);
        return player;
    }

    /**
     * Remove a player from the tracked set. Their stored matches are kept (so
     * head-to-head and past scoreboards still work); they simply stop being
     * refreshed by the scheduler.
     */
    @Transactional
    public Player untrack(String gameName, String tagLine) {
        Player player = requireKnown(gameName, tagLine);
        player.setTracked(false);
        return players.save(player);
    }

    @Transactional(readOnly = true)
    public List<Player> trackedPlayers() {
        return players.findByTrackedTrue();
    }

    public PlayerDto toDto(Player p) {
        return new PlayerDto(
                p.getPuuid(),
                p.getGameName(),
                p.getTagLine(),
                p.getSummonerLevel() == null ? 0 : p.getSummonerLevel(),
                p.getProfileIconId() == null ? 0 : p.getProfileIconId(),
                soloRank(p),
                flexRank(p),
                p.isTracked(),
                p.getLastFetched() == null ? null : p.getLastFetched().toString()
        );
    }

    private RankDto soloRank(Player p) {
        if (p.getSoloTier() == null) {
            return null;
        }
        int wins = p.getSoloWins() == null ? 0 : p.getSoloWins();
        int losses = p.getSoloLosses() == null ? 0 : p.getSoloLosses();
        return new RankDto(SOLO_QUEUE, p.getSoloTier(), p.getSoloRank(),
                p.getSoloLeaguePoints() == null ? 0 : p.getSoloLeaguePoints(),
                wins, losses, StatMath.winRate(wins, wins + losses));
    }

    private RankDto flexRank(Player p) {
        if (p.getFlexTier() == null) {
            return null;
        }
        int wins = p.getFlexWins() == null ? 0 : p.getFlexWins();
        int losses = p.getFlexLosses() == null ? 0 : p.getFlexLosses();
        return new RankDto(FLEX_QUEUE, p.getFlexTier(), p.getFlexRank(),
                p.getFlexLeaguePoints() == null ? 0 : p.getFlexLeaguePoints(),
                wins, losses, StatMath.winRate(wins, wins + losses));
    }
}
