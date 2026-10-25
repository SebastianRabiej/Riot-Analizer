package com.riotanalizer.repository;

import com.riotanalizer.domain.Player;
import com.riotanalizer.domain.TrackingLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, String> {

    Optional<Player> findByGameNameIgnoreCaseAndTagLineIgnoreCase(String gameName, String tagLine);

    List<Player> findByTrackingLevel(TrackingLevel trackingLevel);

    List<Player> findByTrackingLevelNot(TrackingLevel trackingLevel);

    long countByTrackingLevel(TrackingLevel trackingLevel);

    long countByTrackingLevelAndBackfillCompleteTrue(TrackingLevel trackingLevel);

    Page<Player> findByTrackingLevel(TrackingLevel trackingLevel, Pageable page);

    Page<Player> findByTrackingLevelAndGameNameContainingIgnoreCase(TrackingLevel trackingLevel, String gameName,
                                                                   Pageable page);

    /**
     * Players of one tracking level that are due for a check, closest to the
     * tracked players first (lowest discovery depth), then most overdue first.
     */
    @Query("""
            select p from Player p
            where p.trackingLevel = :level
              and (p.nextCheckAt is null or p.nextCheckAt <= :now)
            order by p.discoveryDepth asc nulls first, p.nextCheckAt asc nulls first, p.puuid
            """)
    List<Player> findDue(@Param("level") TrackingLevel level, @Param("now") Instant now, Pageable page);

    @Query("""
            select count(p) from Player p
            where p.trackingLevel = :level
              and (p.nextCheckAt is null or p.nextCheckAt <= :now)
            """)
    long countDue(@Param("level") TrackingLevel level, @Param("now") Instant now);

    /** Players of one tracking level that are not due yet, soonest first. */
    @Query("""
            select p from Player p
            where p.trackingLevel = :level
              and p.nextCheckAt > :now
            order by p.nextCheckAt asc, p.puuid
            """)
    List<Player> findUpcoming(@Param("level") TrackingLevel level, @Param("now") Instant now, Pageable page);

    /**
     * Players of one tracking level that are not due yet but may be checked
     * early (their early_check_at has passed), soonest regular check first.
     */
    @Query("""
            select p from Player p
            where p.trackingLevel = :level
              and p.nextCheckAt > :now
              and (p.earlyCheckAt is null or p.earlyCheckAt <= :now)
            order by p.nextCheckAt asc, p.puuid
            """)
    List<Player> findEarlyEligible(@Param("level") TrackingLevel level, @Param("now") Instant now, Pageable page);

    @Query("""
            select count(p) from Player p
            where p.trackingLevel = :level
              and p.nextCheckAt > :now
              and (p.earlyCheckAt is null or p.earlyCheckAt <= :now)
            """)
    long countEarlyEligible(@Param("level") TrackingLevel level, @Param("now") Instant now);
}
