package com.riotanalizer.repository;

import com.riotanalizer.domain.Participant;
import com.riotanalizer.dto.CarryRow;
import com.riotanalizer.dto.ChampionAggRow;
import com.riotanalizer.dto.LaneOpponentAggRow;
import com.riotanalizer.dto.OpponentAggRow;
import com.riotanalizer.dto.TeammateAggRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    /** All participant rows of one match (all 10 participants). */
    List<Participant> findByMatch_MatchId(String matchId);

    Optional<Participant> findByMatch_MatchIdAndPuuid(String matchId, String puuid);

    /** Used to best-effort resolve a champion name from a champion id via stored data. */
    Optional<Participant> findFirstByChampionId(Integer championId);

    /**
     * A player's own participations within the current season, optional queue filter.
     */
    @Query("""
            select p from Participant p
            where p.puuid = :puuid
              and p.match.gameCreation >= :seasonStart
              and (:queueId is null or p.match.queueId = :queueId)
            order by p.match.gameCreation desc
            """)
    List<Participant> findSeasonParticipations(@Param("puuid") String puuid,
                                               @Param("seasonStart") long seasonStart,
                                               @Param("queueId") Integer queueId);

    /** Paged version of the above, most recent first. */
    @Query("""
            select p from Participant p
            where p.puuid = :puuid
              and p.match.gameCreation >= :seasonStart
              and (:queueId is null or p.match.queueId = :queueId)
            order by p.match.gameCreation desc
            """)
    Page<Participant> findSeasonParticipationsPage(@Param("puuid") String puuid,
                                                   @Param("seasonStart") long seasonStart,
                                                   @Param("queueId") Integer queueId,
                                                   Pageable pageable);

    /**
     * The tracked player's participation rows for current-season matches in which
     * a second given puuid also appears (head-to-head).
     */
    @Query("""
            select p from Participant p
            where p.puuid = :puuid
              and p.match.gameCreation >= :seasonStart
              and p.match.matchId in (
                  select p2.match.matchId from Participant p2 where p2.puuid = :other
              )
            order by p.match.gameCreation desc
            """)
    List<Participant> findSharedSeasonParticipations(@Param("puuid") String puuid,
                                                     @Param("other") String other,
                                                     @Param("seasonStart") long seasonStart);

    /**
     * The tracked player's participation rows for current-season matches in which
     * an ENEMY participant (different team) played the given champion.
     */
    @Query("""
            select p from Participant p
            where p.puuid = :puuid
              and p.match.gameCreation >= :seasonStart
              and exists (
                  select 1 from Participant e
                  where e.match.matchId = p.match.matchId
                    and e.teamId <> p.teamId
                    and lower(e.championName) = lower(:champion)
              )
            order by p.match.gameCreation desc
            """)
    List<Participant> findSeasonParticipationsVsChampion(@Param("puuid") String puuid,
                                                         @Param("champion") String champion,
                                                         @Param("seasonStart") long seasonStart);

    /**
     * "Vs player" dashboard aggregation: every opponent the tracked player has met
     * on the ENEMY team this season, grouped by opponent puuid, with games faced
     * and the tracked player's wins in those games. Only opponents met at least
     * {@code minGames} times are returned, most-faced first.
     */
    @Query("""
            select new com.riotanalizer.dto.OpponentAggRow(
                e.puuid,
                max(e.riotIdGameName),
                max(e.riotIdTagLine),
                count(e),
                sum(case when me.win = true then 1L else 0L end)
            )
            from Participant me
            join me.match m
            join m.participants e
            where me.puuid = :puuid
              and m.gameCreation >= :seasonStart
              and e.teamId <> me.teamId
              and e.puuid is not null
            group by e.puuid
            having count(e) >= :minGames
            order by count(e) desc
            """)
    List<OpponentAggRow> aggregateOpponents(@Param("puuid") String puuid,
                                            @Param("seasonStart") long seasonStart,
                                            @Param("minGames") long minGames);

    /**
     * "Vs champion" dashboard aggregation: every enemy champion the tracked player
     * has faced this season, grouped by champion, with games faced, the player's
     * wins, and the player's K/D/A totals in those games. Most-faced first.
     */
    @Query("""
            select new com.riotanalizer.dto.ChampionAggRow(
                e.championId,
                e.championName,
                count(distinct m.matchId),
                sum(case when me.win = true then 1L else 0L end),
                sum(me.kills),
                sum(me.deaths),
                sum(me.assists)
            )
            from Participant me
            join me.match m
            join m.participants e
            where me.puuid = :puuid
              and m.gameCreation >= :seasonStart
              and e.teamId <> me.teamId
              and e.championName is not null
            group by e.championId, e.championName
            order by count(distinct m.matchId) desc
            """)
    List<ChampionAggRow> aggregateChampionMatchups(@Param("puuid") String puuid,
                                                   @Param("seasonStart") long seasonStart);
    /**
     * "With player" (duo) aggregation: every teammate the tracked player has
     * played alongside on the SAME team this season, grouped by teammate puuid,
     * with shared games and wins (same team => shared outcome). Only teammates
     * met at least {@code minGames} times are returned, most-played first.
     */
    @Query("""
            select new com.riotanalizer.dto.TeammateAggRow(
                t.puuid,
                max(t.riotIdGameName),
                max(t.riotIdTagLine),
                count(t),
                sum(case when me.win = true then 1L else 0L end)
            )
            from Participant me
            join me.match m
            join m.participants t
            where me.puuid = :puuid
              and m.gameCreation >= :seasonStart
              and t.teamId = me.teamId
              and t.puuid <> me.puuid
              and t.puuid is not null
            group by t.puuid
            having count(t) >= :minGames
            order by count(t) desc
            """)
    List<TeammateAggRow> aggregateTeammates(@Param("puuid") String puuid,
                                            @Param("seasonStart") long seasonStart,
                                            @Param("minGames") long minGames);

    /**
     * "Lane opponents" aggregation: every enemy champion the tracked player has
     * faced in the SAME position (their actual lane opponent) this season,
     * grouped by champion, with games, wins, and the player's K/D/A totals.
     * Games without a stored position for the player are excluded. Most-faced first.
     */
    @Query("""
            select new com.riotanalizer.dto.LaneOpponentAggRow(
                e.championId,
                e.championName,
                count(distinct m.matchId),
                sum(case when me.win = true then 1L else 0L end),
                sum(me.kills),
                sum(me.deaths),
                sum(me.assists)
            )
            from Participant me
            join me.match m
            join m.participants e
            where me.puuid = :puuid
              and m.gameCreation >= :seasonStart
              and e.teamId <> me.teamId
              and e.teamPosition = me.teamPosition
              and me.teamPosition is not null
              and me.teamPosition <> ''
              and e.championName is not null
            group by e.championId, e.championName
            order by count(distinct m.matchId) desc
            """)
    List<LaneOpponentAggRow> aggregateLaneOpponents(@Param("puuid") String puuid,
                                                    @Param("seasonStart") long seasonStart);

    /**
     * Carry Index aggregation: one row per current-season game of the tracked
     * player, carrying the player's own damage/gold/kills/assists plus the totals
     * (and top damage) of their team that game. Team sums include the player, so
     * a share is the player's fraction of their team's output. Optional queue,
     * champion and position filters mirror the other stat endpoints. Newest first.
     */
    @Query("""
            select new com.riotanalizer.dto.CarryRow(
                m.matchId,
                m.gameCreation,
                me.championName,
                me.teamPosition,
                me.win,
                me.totalDamageToChampions,
                me.goldEarned,
                me.kills,
                me.assists,
                sum(t.totalDamageToChampions),
                sum(t.goldEarned),
                sum(t.kills),
                sum(t.assists),
                max(t.totalDamageToChampions))
            from Participant me
            join me.match m
            join m.participants t
            where me.puuid = :puuid
              and m.gameCreation >= :seasonStart
              and t.teamId = me.teamId
              and (:queueId is null or m.queueId = :queueId)
              and (:champion is null or lower(me.championName) = :champion)
              and (:position is null or upper(me.teamPosition) = :position)
            group by m.matchId, m.gameCreation, me.championName, me.teamPosition,
                     me.win, me.totalDamageToChampions, me.goldEarned, me.kills, me.assists
            order by m.gameCreation desc
            """)
    List<CarryRow> aggregateCarry(@Param("puuid") String puuid,
                                  @Param("seasonStart") long seasonStart,
                                  @Param("queueId") Integer queueId,
                                  @Param("champion") String champion,
                                  @Param("position") String position);
}
