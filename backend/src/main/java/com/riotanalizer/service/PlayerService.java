package com.riotanalizer.service;

import com.riotanalizer.client.RiotApiClient;
import com.riotanalizer.client.dto.AccountDto;
import com.riotanalizer.client.dto.LeagueEntryDto;
import com.riotanalizer.client.dto.SummonerDto;
import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import com.riotanalizer.dto.DiscoveredPageDto;
import com.riotanalizer.dto.PlayerDto;
import com.riotanalizer.dto.RankDto;
import com.riotanalizer.exception.NotFoundException;
import com.riotanalizer.exception.RiotApiException;
import com.riotanalizer.repository.PlayerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PlayerService {

    private static final String SOLO_QUEUE = "RANKED_SOLO_5x5";
    private static final String FLEX_QUEUE = "RANKED_FLEX_SR";

    private final RiotApiClient riot;

    /** {@code riot.discovery-fetch-matches}, shown on the discovered page. */
    @Value("${riot.discovery-fetch-matches:false}")
    private boolean discoveredFetchMatches;
    private final PlayerRepository players;
    private final FetchService fetchService;
    private final PlayerRekeyService playerRekey;

    public PlayerService(RiotApiClient riot, PlayerRepository players, FetchService fetchService,
                         PlayerRekeyService playerRekey) {
        this.riot = riot;
        this.players = players;
        this.fetchService = fetchService;
        this.playerRekey = playerRekey;
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
     * Refresh summoner + rank info from Riot and persist. If Riot rejects the
     * stored PUUID as issued for another API key, this player is re-keyed right
     * away (the rest get flagged and re-checked by {@link PuuidRekeyWorker}).
     * Always continue with the returned player — its PUUID may have changed.
     */
    @Transactional
    public Player refreshProfile(Player player) {
        try {
            refreshProfileFields(player);
            return players.save(player);
        } catch (RiotApiException e) {
            if (!e.isDecryptionError()) {
                throw e;
            }
            Player rekeyed = playerRekey.rekey(player).orElseThrow(() -> e);
            // The DB merge keeps the stronger level; this covers an in-memory
            // track() that hasn't been saved yet.
            if (player.isTracked()) {
                rekeyed.setTracked(true);
            }
            refreshProfileFields(rekeyed);
            return players.save(rekeyed);
        }
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
     * Manual refresh: profile + match sync (a 1-call head check once the season
     * is backfilled, otherwise the next backfill batch).
     *
     * <p>Deliberately NOT {@code @Transactional}: the backfill can page through
     * hundreds of rate-limited Riot calls, and wrapping that in a single
     * transaction would pin a DB connection for the whole time. Profile refresh
     * and each match are persisted in their own transactions instead.
     */
    public Player refresh(String gameName, String tagLine) {
        Player player = refreshProfile(getOrRegister(gameName, tagLine));
        fetchService.sync(player);
        return save(player);
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
        player = refreshProfile(player);
        fetchService.sync(player);
        return save(player);
    }

    /**
     * Remove a player from the tracked set (back to NONE). Their stored matches
     * are kept (so head-to-head and past scoreboards still work); they simply
     * stop being refreshed by the scheduler. If they show up in a tracked
     * player's new match later, discovery may pick them up as DISCOVERED.
     */
    @Transactional
    public Player untrack(String gameName, String tagLine) {
        Player player = requireKnown(gameName, tagLine);
        player.setTracked(false);
        return players.save(player);
    }

    @Transactional(readOnly = true)
    public List<Player> trackedPlayers() {
        return players.findByTrackingLevel(TrackingLevel.TRACKED);
    }

    /** Persist scheduling / sync state changes. */
    @Transactional
    public Player save(Player player) {
        return players.save(player);
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
                iso(p.getLastFetched()),
                p.getTrackingLevel().name(),
                p.getDiscoveryDepth(),
                p.isBackfillComplete(),
                iso(p.getNewestMatchAt()),
                iso(p.getNextCheckAt())
        );
    }

    private static String iso(Instant t) {
        return t == null ? null : t.toString();
    }

    /**
     * A page of DISCOVERED players, closest discovery depth first, optionally
     * filtered by a case-insensitive Riot ID name fragment.
     */
    @Transactional(readOnly = true)
    public DiscoveredPageDto discovered(int page, int size, String query) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        PageRequest request = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.asc("discoveryDepth"), Sort.Order.asc("gameName"), Sort.Order.asc("puuid")));
        Page<Player> result = (query == null || query.isBlank())
                ? players.findByTrackingLevel(TrackingLevel.DISCOVERED, request)
                : players.findByTrackingLevelAndGameNameContainingIgnoreCase(
                        TrackingLevel.DISCOVERED, query.trim(), request);
        return new DiscoveredPageDto(
                players.countByTrackingLevel(TrackingLevel.DISCOVERED),
                players.countByTrackingLevelAndBackfillCompleteTrue(TrackingLevel.DISCOVERED),
                players.countByTrackingLevelAndLastFetchedIsNotNull(TrackingLevel.DISCOVERED),
                discoveredFetchMatches,
                result.getTotalElements(),
                safePage,
                safeSize,
                result.getContent().stream().map(this::toDto).toList());
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
