package com.riotanalizer.repository;

import com.riotanalizer.domain.MatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MatchRepository extends JpaRepository<MatchEntity, String> {

    /** Load a match together with all of its participants in one query. */
    @Query("select distinct m from MatchEntity m left join fetch m.participants where m.matchId = :matchId")
    Optional<MatchEntity> findWithParticipants(@Param("matchId") String matchId);

    /** Just the creation time (epoch ms) of a stored match, without loading participants. */
    @Query("select m.gameCreation from MatchEntity m where m.matchId = :matchId")
    Optional<Long> findGameCreation(@Param("matchId") String matchId);
}
