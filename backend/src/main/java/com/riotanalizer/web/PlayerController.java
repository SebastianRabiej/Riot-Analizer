package com.riotanalizer.web;

import com.riotanalizer.domain.Player;
import com.riotanalizer.dto.CarryIndexDto;
import com.riotanalizer.dto.ChampionMatchupSummaryDto;
import com.riotanalizer.dto.GameLengthStatsDto;
import com.riotanalizer.dto.HeadToHeadDto;
import com.riotanalizer.dto.LiveMatchDto;
import com.riotanalizer.dto.MatchSummaryDto;
import com.riotanalizer.dto.OpponentSummaryDto;
import com.riotanalizer.dto.PlayerDto;
import com.riotanalizer.dto.PerformanceTrendDto;
import com.riotanalizer.dto.PlayerStatsDto;
import com.riotanalizer.dto.InsightsDto;
import com.riotanalizer.dto.LaneOpponentSummaryDto;
import com.riotanalizer.dto.QueueAdviceDto;
import com.riotanalizer.dto.RolesDto;
import com.riotanalizer.dto.TeammateSummaryDto;
import com.riotanalizer.dto.TimeStatsDto;
import com.riotanalizer.dto.VsChampionDto;
import com.riotanalizer.service.CarryService;
import com.riotanalizer.service.GameLengthService;
import com.riotanalizer.service.HeadToHeadService;
import com.riotanalizer.service.InsightsService;
import com.riotanalizer.service.LiveGameService;
import com.riotanalizer.service.MatchupsService;
import com.riotanalizer.service.PerformanceService;
import com.riotanalizer.service.PlayerService;
import com.riotanalizer.service.QueueAdviceService;
import com.riotanalizer.service.RolesService;
import com.riotanalizer.service.StatsService;
import com.riotanalizer.service.TimeStatsService;
import com.riotanalizer.service.VsChampionService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PlayerController {

    private final PlayerService playerService;
    private final StatsService statsService;
    private final LiveGameService liveGameService;
    private final HeadToHeadService headToHeadService;
    private final VsChampionService vsChampionService;
    private final MatchupsService matchupsService;
    private final RolesService rolesService;
    private final TimeStatsService timeStatsService;
    private final InsightsService insightsService;
    private final PerformanceService performanceService;
    private final CarryService carryService;
    private final GameLengthService gameLengthService;
    private final QueueAdviceService queueAdviceService;

    public PlayerController(PlayerService playerService, StatsService statsService,
                           LiveGameService liveGameService, HeadToHeadService headToHeadService,
                           VsChampionService vsChampionService, MatchupsService matchupsService,
                           RolesService rolesService, TimeStatsService timeStatsService,
                           InsightsService insightsService, PerformanceService performanceService,
                           CarryService carryService, GameLengthService gameLengthService,
                           QueueAdviceService queueAdviceService) {
        this.playerService = playerService;
        this.statsService = statsService;
        this.liveGameService = liveGameService;
        this.headToHeadService = headToHeadService;
        this.vsChampionService = vsChampionService;
        this.matchupsService = matchupsService;
        this.rolesService = rolesService;
        this.timeStatsService = timeStatsService;
        this.insightsService = insightsService;
        this.performanceService = performanceService;
        this.carryService = carryService;
        this.gameLengthService = gameLengthService;
        this.queueAdviceService = queueAdviceService;
    }

    @GetMapping("/players/{gameName}/{tagLine}")
    public PlayerDto getPlayer(@PathVariable String gameName, @PathVariable String tagLine) {
        return playerService.toDto(playerService.getOrRegister(gameName, tagLine));
    }

    @GetMapping("/players/{gameName}/{tagLine}/stats")
    public PlayerStatsDto stats(@PathVariable String gameName, @PathVariable String tagLine,
                                @RequestParam(required = false) Integer queue) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return statsService.playerStats(player, queue);
    }

    @GetMapping("/players/{gameName}/{tagLine}/matches")
    public Page<MatchSummaryDto> matches(@PathVariable String gameName, @PathVariable String tagLine,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) Integer queue) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return statsService.matches(player, page, size, queue);
    }

    @GetMapping("/players/{gameName}/{tagLine}/live")
    public LiveMatchDto live(@PathVariable String gameName, @PathVariable String tagLine) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return liveGameService.getLive(player.getPuuid());
    }

    @GetMapping("/players/{gameName}/{tagLine}/vs/{oppGameName}/{oppTagLine}")
    public HeadToHeadDto vsPlayer(@PathVariable String gameName, @PathVariable String tagLine,
                                  @PathVariable String oppGameName, @PathVariable String oppTagLine) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        Player opponent = playerService.getOrRegister(oppGameName, oppTagLine);
        return headToHeadService.compute(player, opponent);
    }

    @GetMapping("/players/{gameName}/{tagLine}/vs-champion/{championName}")
    public VsChampionDto vsChampion(@PathVariable String gameName, @PathVariable String tagLine,
                                    @PathVariable String championName) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return vsChampionService.compute(player, championName);
    }

    /** "Vs player" dashboard: every opponent faced this season (min games filter). */
    @GetMapping("/players/{gameName}/{tagLine}/opponents")
    public List<OpponentSummaryDto> opponents(@PathVariable String gameName, @PathVariable String tagLine,
                                              @RequestParam(defaultValue = "2") int minGames,
                                              @RequestParam(defaultValue = "200") int limit) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return matchupsService.opponents(player, minGames, limit);
    }

    /** "With player" (duo) dashboard: every teammate played alongside this season. */
    @GetMapping("/players/{gameName}/{tagLine}/teammates")
    public List<TeammateSummaryDto> teammates(@PathVariable String gameName, @PathVariable String tagLine,
                                              @RequestParam(defaultValue = "2") int minGames,
                                              @RequestParam(defaultValue = "200") int limit) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return matchupsService.teammates(player, minGames, limit);
    }

    /** Lane opponents: enemy champions faced in the tracked player's own position. */
    @GetMapping("/players/{gameName}/{tagLine}/lane-opponents")
    public List<LaneOpponentSummaryDto> laneOpponents(@PathVariable String gameName,
                                                      @PathVariable String tagLine,
                                                      @RequestParam(defaultValue = "200") int limit) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return matchupsService.laneOpponents(player, limit);
    }

    /** Per-role (lane/position) performance this season. */
    @GetMapping("/players/{gameName}/{tagLine}/roles")
    public RolesDto roles(@PathVariable String gameName, @PathVariable String tagLine,
                          @RequestParam(required = false) Integer queue,
                          @RequestParam(required = false) String champion,
                          @RequestParam(required = false) String role) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return rolesService.roles(player, queue, champion, role);
    }

    /** "When do you play best": win rate by hour, weekday, and session position. */
    @GetMapping("/players/{gameName}/{tagLine}/time")
    public TimeStatsDto time(@PathVariable String gameName, @PathVariable String tagLine,
                             @RequestParam(required = false) Integer queue,
                             @RequestParam(defaultValue = "0") int tz,
                             @RequestParam(required = false) String champion,
                             @RequestParam(required = false) String role) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return timeStatsService.timeStats(player, queue, tz, champion, role);
    }

    /** Auto-generated insights: streaks, nemesis, best duo, bogey pick, role, tilt. */
    @GetMapping("/players/{gameName}/{tagLine}/insights")
    public InsightsDto insights(@PathVariable String gameName, @PathVariable String tagLine,
                                @RequestParam(required = false) Integer queue,
                                @RequestParam(defaultValue = "0") int tz) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return insightsService.compute(player, queue, tz);
    }

    /** "Performance over time": per-game metric series (oldest first) for trend charts. */
    @GetMapping("/players/{gameName}/{tagLine}/performance")
    public PerformanceTrendDto performance(@PathVariable String gameName, @PathVariable String tagLine,
                                           @RequestParam(required = false) Integer queue) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return performanceService.performanceTrend(player, queue);
    }

    /** Carry Index: your share of your team's output, and whether carrying wins. */
    @GetMapping("/players/{gameName}/{tagLine}/carry")
    public CarryIndexDto carry(@PathVariable String gameName, @PathVariable String tagLine,
                               @RequestParam(required = false) Integer queue,
                               @RequestParam(required = false) String champion,
                               @RequestParam(required = false) String role) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return carryService.carry(player, queue, champion, role);
    }

    /** Win rate by game length: do you stomp early or grind out long games? */
    @GetMapping("/players/{gameName}/{tagLine}/game-length")
    public GameLengthStatsDto gameLength(@PathVariable String gameName, @PathVariable String tagLine,
                                         @RequestParam(required = false) Integer queue,
                                         @RequestParam(required = false) String champion,
                                         @RequestParam(required = false) String role) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return gameLengthService.gameLength(player, queue, champion, role);
    }

    /** "Should I queue again?": a live GO / CAUTION / STOP read for the next game. */
    @GetMapping("/players/{gameName}/{tagLine}/queue-advice")
    public QueueAdviceDto queueAdvice(@PathVariable String gameName, @PathVariable String tagLine,
                                      @RequestParam(required = false) Integer queue,
                                      @RequestParam(defaultValue = "0") int tz) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return queueAdviceService.advice(player, queue, tz);
    }

    /** "Vs champion" dashboard: every enemy champion faced this season. */
    @GetMapping("/players/{gameName}/{tagLine}/champion-matchups")
    public List<ChampionMatchupSummaryDto> championMatchups(@PathVariable String gameName,
                                                            @PathVariable String tagLine,
                                                            @RequestParam(defaultValue = "300") int limit) {
        Player player = playerService.getOrRegister(gameName, tagLine);
        return matchupsService.championMatchups(player, limit);
    }

    @PostMapping("/players/{gameName}/{tagLine}/refresh")
    public PlayerDto refresh(@PathVariable String gameName, @PathVariable String tagLine) {
        return playerService.toDto(playerService.refresh(gameName, tagLine));
    }

    @PostMapping("/players/{gameName}/{tagLine}/track")
    public PlayerDto track(@PathVariable String gameName, @PathVariable String tagLine) {
        return playerService.toDto(playerService.track(gameName, tagLine));
    }

    @DeleteMapping("/players/{gameName}/{tagLine}/track")
    public PlayerDto untrack(@PathVariable String gameName, @PathVariable String tagLine) {
        return playerService.toDto(playerService.untrack(gameName, tagLine));
    }

    @GetMapping("/tracked")
    public List<PlayerDto> tracked() {
        return playerService.trackedPlayers().stream().map(playerService::toDto).toList();
    }
}
