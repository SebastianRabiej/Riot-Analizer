// Types mirroring the shared API contract (docs/API_CONTRACT.md).
// Keep these shapes in exact sync with the backend DTOs.

export interface RankDto {
  queueType: string;
  tier: string;
  rank: string;
  leaguePoints: number;
  wins: number;
  losses: number;
  winRate: number;
}

export interface PlayerDto {
  puuid: string;
  gameName: string;
  tagLine: string;
  summonerLevel: number;
  profileIconId: number;
  soloRank: RankDto | null;
  flexRank: RankDto | null;
  tracked: boolean;
  lastFetched: string | null; // ISO
  /** TRACKED (chosen by a user), DISCOVERED (met in a tracked player's match) or NONE. */
  trackingLevel?: TrackingLevel;
  /** Hops from a tracked player: 0 = tracked, 1 = met in a tracked player's game… */
  discoveryDepth?: number | null;
  /** Whole current season stored; from now on only new games are fetched. */
  backfillComplete?: boolean;
  newestMatchAt?: string | null; // ISO, newest game in their match list
  nextCheckAt?: string | null; // ISO, when the scheduler looks at them next (null = due now)
}

export type TrackingLevel = 'TRACKED' | 'DISCOVERED' | 'NONE';

export interface DiscoveredPageDto {
  total: number;
  seasonSynced: number;
  matching: number;
  page: number;
  size: number;
  players: PlayerDto[];
}

/** One player in the background scheduler's queue (GET /api/fetch-queue). */
export interface FetchQueueEntry {
  /** 1-based place in the "up next" order; null for players not due yet. */
  position: number | null;
  /** This place in "up next" is an early check: not due yet, picked because the API budget has spare room. */
  early: boolean;
  puuid: string;
  gameName: string | null;
  tagLine: string | null;
  profileIconId: number;
  trackingLevel: TrackingLevel;
  discoveryDepth: number | null;
  nextCheckAt: string | null; // ISO; null = never checked
  /** Earliest time an early (spare-budget) check may happen; null = any time. */
  earlyCheckAt: string | null;
  newestMatchAt: string | null;
  /** HEAD_CHECK = 1-call "anything new?"; BACKFILL = still walking the season. */
  checkType: 'HEAD_CHECK' | 'BACKFILL';
  steps: string[];
  minCalls: number;
  maxCalls: number;
  reason: string;
  inGame: boolean | null;
}

export interface FetchQueueDto {
  generatedAt: string;
  schedulerTickMs: number;
  lastRunAt: string | null;
  lastRunChecked: number;
  running: {
    puuid: string;
    gameName: string | null;
    tagLine: string | null;
    trackingLevel: TrackingLevel;
    startedAt: string;
    checkType: 'HEAD_CHECK' | 'BACKFILL';
    early: boolean;
  } | null;
  dueTracked: number;
  dueDiscovered: number;
  /** Not due yet but eligible for an early check (min gap since the last check has passed). */
  earlyTracked: number;
  earlyDiscovered: number;
  earlyChecks: EarlyChecks;
  next: FetchQueueEntry[];
  upcomingTracked: FetchQueueEntry[];
  upcomingDiscovered: FetchQueueEntry[];
  rateLimit: RateLimitDto;
}

/** Early (spare-budget) checks: players are pulled forward while the long window has headroom. */
export interface EarlyChecks {
  enabled: boolean;
  longUsed: number;
  longLimit: number;
  /** Long-window permits that must be free for an early check. */
  trackedMinFree: number;
  discoveredMinFree: number;
  /** Would an early check be allowed right now? */
  trackedOpen: boolean;
  discoveredOpen: boolean;
  trackedMinGapMs: number;
  discoveredMinGapMs: number;
}

export type FetchPriority = 'REFRESH' | 'ANALYSIS' | 'TRACKED_HISTORY' | 'DISCOVERED_HISTORY';

/** Riot API budget. "...InMs" values are relative to the snapshot time. */
export interface RateLimitDto {
  shortWindow: RateLimitWindow;
  longWindow: RateLimitWindow;
  /** Highest priority first. */
  priorities: {
    priority: FetchPriority;
    longLimit: number; // long-window calls this priority may use (limit minus reserve)
    reserve: number;
    freeInMs: number; // 0 = can call now (budget-wise)
    waiting: number;
  }[];
  /** Riot's own count from response headers; null before the first call. */
  riot: {
    observedAt: string | null;
    appWindows: { windowSec: number; count: number; limit: number }[];
    rateLimitedCount: number;
    lastRateLimitedAt: string | null;
    lastRetryAfterSec: number | null;
    lastRateLimitType: string | null;
  } | null;
}

export interface RateLimitWindow {
  used: number;
  limit: number;
  windowMs: number;
  nextExpiryInMs: number | null;
}

export interface ChampionStatDto {
  championId: number;
  championName: string;
  games: number;
  wins: number;
  losses: number;
  winRate: number;
  avgKills: number;
  avgDeaths: number;
  avgAssists: number;
  avgKda: number;
  avgCsPerMin: number;
}

export interface PlayerStatsDto {
  gameName: string;
  tagLine: string;
  gamesPlayed: number;
  wins: number;
  losses: number;
  winRate: number; // 0..100
  avgKills: number;
  avgDeaths: number;
  avgAssists: number;
  avgKda: number;
  avgCsPerMin: number;
  avgVisionScore: number;
  avgDamageToChampions: number;
  avgGoldEarned: number;
  avgGameDurationSec: number;
  recentForm: boolean[]; // most-recent-first, true=win, up to 20
  championStats: ChampionStatDto[]; // sorted by games desc
}

export interface MatchSummaryDto {
  matchId: string;
  queueId: number;
  queueName: string;
  gameCreation: number; // epoch ms
  gameDurationSec: number;
  championId: number;
  championName: string;
  win: boolean;
  kills: number;
  deaths: number;
  assists: number;
  kda: number;
  cs: number;
  csPerMin: number;
  visionScore: number;
  damageToChampions: number;
  goldEarned: number;
  teamPosition: string;
  items: number[]; // length 7
  summonerSpell1: number;
  summonerSpell2: number;
}

export interface LiveParticipantDto {
  puuid: string;
  riotId: string | null; // gameName#tagLine or null
  championId: number;
  championName: string;
  teamId: number;
  spell1Id: number;
  spell2Id: number;
  // Solo-queue rank: tier/rank/leaguePoints when ranked, unranked=true when
  // unranked, all absent when the lookup failed.
  tier?: string;
  rank?: string;
  leaguePoints?: number;
  unranked?: boolean;
}

export interface LiveMatchDto {
  inGame: boolean;
  gameId?: number;
  queueId?: number;
  gameMode?: string;
  gameStartTime?: number; // epoch ms
  gameLengthSec?: number;
  mapId?: number;
  participants?: LiveParticipantDto[];
}

export interface H2HMatchDto {
  matchId: string;
  gameCreation: number;
  sameTeam: boolean;
  playerChampionName: string;
  opponentChampionName: string;
  playerWin: boolean;
  playerKills: number;
  playerDeaths: number;
  playerAssists: number;
  opponentKills: number;
  opponentDeaths: number;
  opponentAssists: number;
  queueName: string;
}

export interface HeadToHeadDto {
  player: { gameName: string; tagLine: string };
  opponent: { gameName: string; tagLine: string };
  totalSharedGames: number;
  sameTeamGames: number;
  opposingGames: number;
  playerWinsWhenOpposing: number;
  opponentWinsWhenOpposing: number;
  matches: H2HMatchDto[];
}

export interface VsChampionMatchDto {
  matchId: string;
  gameCreation: number;
  playerChampionName: string;
  win: boolean;
  kills: number;
  deaths: number;
  assists: number;
  queueName: string;
}

export interface VsChampionDto {
  gameName: string;
  tagLine: string;
  championName: string;
  gamesAgainst: number;
  wins: number;
  losses: number;
  winRate: number;
  avgKills: number;
  avgDeaths: number;
  avgAssists: number;
  avgKda: number;
  matches: VsChampionMatchDto[];
}

export interface OpponentSummaryDto {
  puuid: string;
  gameName: string | null;
  tagLine: string | null;
  games: number;
  wins: number;
  losses: number;
  winRate: number; // 0..100
}

export interface ChampionMatchupSummaryDto {
  championId: number | null;
  championName: string;
  games: number;
  wins: number;
  losses: number;
  winRate: number; // 0..100
  avgKills: number;
  avgDeaths: number;
  avgAssists: number;
  avgKda: number;
}

// Spring Data Page<T> JSON envelope.
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page index
  size: number;
}

// Admin session state (GET /api/auth/me).
export interface AuthStatus {
  authenticated: boolean;
  username: string | null;
  /** False when the server has no admin password configured. */
  loginEnabled: boolean;
}

// Error envelope returned by the backend.
export interface ApiError {
  error: string;
  status: number;
  path: string;
  timestamp: string;
}

// ---- Teammate / duo synergy ----
export interface TeammateSummaryDto {
  puuid: string;
  gameName: string | null;
  tagLine: string | null;
  games: number;
  wins: number;
  losses: number;
  winRate: number; // 0..100
}

// ---- Lane opponents (enemy champion in your own position) ----
export interface LaneOpponentSummaryDto {
  championId: number | null;
  championName: string;
  games: number;
  wins: number;
  losses: number;
  winRate: number; // 0..100
  avgKills: number;
  avgDeaths: number;
  avgAssists: number;
  avgKda: number;
}

// ---- Roles / positions ----
export interface RoleStatDto {
  position: string; // TOP | JUNGLE | MIDDLE | BOTTOM | UTILITY | NONE
  games: number;
  wins: number;
  losses: number;
  winRate: number; // 0..100
  avgKills: number;
  avgDeaths: number;
  avgAssists: number;
  avgKda: number;
  avgCsPerMin: number;
  avgVisionScore: number;
}

export interface RolesDto {
  gameName: string;
  tagLine: string;
  totalGames: number;
  roles: RoleStatDto[];
}

// ---- Time & tilt ----
export interface TimeBucketDto {
  key: number;
  label: string;
  games: number;
  wins: number;
  winRate: number; // 0..100
}

export interface TimeStatsDto {
  gameName: string;
  tagLine: string;
  totalGames: number;
  byHour: TimeBucketDto[]; // 24
  byWeekday: TimeBucketDto[]; // 7, Mon..Sun
  bySession: TimeBucketDto[]; // by position within a play session
}

// ---- Auto-insights ----
export interface InsightDto {
  kind: string;
  icon: string;
  title: string;
  detail: string;
  sentiment: 'good' | 'bad' | 'neutral';
  linkKind: 'vs-player' | 'with-player' | 'vs-champion' | 'trends' | null;
  linkValue: string | null;
}

export interface InsightsDto {
  gameName: string;
  tagLine: string;
  gamesAnalyzed: number;
  currentStreak: number; // signed: + win streak, - loss streak
  longestWinStreak: number;
  longestLossStreak: number;
  highlights: InsightDto[];
}

// ---- Full match detail (scoreboard) ----
export interface MatchParticipantDetailDto {
  puuid: string;
  gameName: string | null;
  tagLine: string | null;
  championId: number;
  championName: string;
  teamId: number;
  teamPosition: string;
  win: boolean;
  kills: number;
  deaths: number;
  assists: number;
  kda: number;
  cs: number;
  csPerMin: number;
  visionScore: number;
  damageToChampions: number;
  goldEarned: number;
  items: number[]; // length 7
  summonerSpell1: number;
  summonerSpell2: number;
}

export interface MatchTeamDto {
  teamId: number; // 100 blue, 200 red
  win: boolean;
  kills: number;
  deaths: number;
  assists: number;
  goldEarned: number;
  damageToChampions: number;
  participants: MatchParticipantDetailDto[];
}

export interface MatchDetailDto {
  matchId: string;
  queueId: number;
  queueName: string;
  gameCreation: number;
  gameDurationSec: number;
  mapId: number | null;
  gameVersion: string | null;
  teams: MatchTeamDto[];
}

// ---- Match timeline analysis ----
export interface TimelineParticipant {
  participantId: number;
  puuid: string;
  teamId: number;
  championName: string | null;
  teamPosition: string | null;
}

export interface TimelineParticipantFrame {
  participantId: number;
  totalGold: number;
  xp: number;
  cs: number;
  level: number;
  x: number;
  y: number;
  damage: number;
  currentGold: number;
}

export interface TimelineFrame {
  timestampMs: number;
  participants: TimelineParticipantFrame[];
}

export interface TimelineEvent {
  timestampMs: number;
  type: string;
  participantId: number | null;
  killerId: number | null;
  victimId: number | null;
  assistIds: number[] | null;
  teamId: number | null;
  subType: string | null;
  lane: string | null;
  x: number | null;
  y: number | null;
  bounty: number | null;
  shutdownBounty: number | null;
  wardType: string | null;
  level: number | null;
  itemId: number | null;
}

export interface TimelineAnalysis {
  matchId: string;
  frameIntervalMs: number;
  queueId?: number | null;
  mapId?: number | null;
  participants: TimelineParticipant[];
  frames: TimelineFrame[];
  events: TimelineEvent[];
}

// ---- Performance over time (per-game trend) ----
export interface PerfPoint {
  gameCreation: number; // epoch ms
  matchId: string;
  championId: number;
  championName: string;
  teamPosition: string;
  win: boolean;
  kills: number;
  deaths: number;
  assists: number;
  kda: number;
  cs: number;
  csPerMin: number;
  visionScore: number;
  damageToChampions: number;
  goldEarned: number;
  gameDurationSec: number;
  queueId: number;
}

export interface PerformanceTrend {
  gameName: string;
  tagLine: string;
  totalGames: number;
  points: PerfPoint[]; // oldest first
}

export interface CarryPoint {
  matchId: string;
  gameCreation: number; // epoch ms
  championName: string;
  teamPosition: string;
  win: boolean;
  damageShare: number; // 0..100
  goldShare: number; // 0..100
  killParticipation: number; // 0..100
  topDamage: boolean;
  carryScore: number; // 0..100
}

export interface CarryIndexDto {
  gameName: string;
  tagLine: string;
  games: number;
  avgDamageShare: number; // 0..100
  avgGoldShare: number; // 0..100
  avgKillParticipation: number; // 0..100
  topDamageRate: number; // 0..100
  winRateWhenTopDamage: number; // 0..100
  winRateOtherwise: number; // 0..100
  carryIndex: number; // 0..100
  perGame: CarryPoint[]; // oldest first
}

export interface DurationBucket {
  key: number;
  label: string;
  minSec: number;
  maxSec: number;
  games: number;
  wins: number;
  winRate: number;
}

export interface GameLengthStats {
  gameName: string;
  tagLine: string;
  total: number;
  buckets: DurationBucket[];
  avgWinDurationSec: number;
  avgLossDurationSec: number;
}

export interface QueueAdviceReason {
  text: string;
  tone: 'good' | 'bad' | 'neutral';
}

export interface QueueAdvice {
  gameName: string;
  tagLine: string;
  signal: 'GO' | 'CAUTION' | 'STOP';
  predictedWinRate: number;
  sessionPositionNext: number;
  minutesSinceLastGame: number; // -1 when no games
  sampleSize: number;
  reasons: QueueAdviceReason[];
}
