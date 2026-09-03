package com.riotanalizer.web;

import com.riotanalizer.dto.MatchDetailDto;
import com.riotanalizer.dto.TimelineAnalysisDto;
import com.riotanalizer.service.MatchDetailService;
import com.riotanalizer.service.MatchTimelineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MatchController {

    private final MatchDetailService matchDetailService;
    private final MatchTimelineService matchTimelineService;

    public MatchController(MatchDetailService matchDetailService,
                           MatchTimelineService matchTimelineService) {
        this.matchDetailService = matchDetailService;
        this.matchTimelineService = matchTimelineService;
    }

    /** Full scoreboard for a single match: both teams, all participants. */
    @GetMapping("/matches/{matchId}")
    public MatchDetailDto match(@PathVariable String matchId) {
        return matchDetailService.detail(matchId);
    }

    /** Distilled timeline for a match (kills, gold/xp/cs per minute, objectives). */
    @GetMapping("/matches/{matchId}/analysis")
    public TimelineAnalysisDto analysis(@PathVariable String matchId) {
        return matchTimelineService.analysis(matchId);
    }
}
