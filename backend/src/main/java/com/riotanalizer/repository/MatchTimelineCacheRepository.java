package com.riotanalizer.repository;

import com.riotanalizer.domain.MatchTimelineCache;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchTimelineCacheRepository extends JpaRepository<MatchTimelineCache, String> {
}
