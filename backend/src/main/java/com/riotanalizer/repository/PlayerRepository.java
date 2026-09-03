package com.riotanalizer.repository;

import com.riotanalizer.domain.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, String> {

    Optional<Player> findByGameNameIgnoreCaseAndTagLineIgnoreCase(String gameName, String tagLine);

    List<Player> findByTrackedTrue();
}
