import type {
  PlayerDto,
  PlayerStatsDto,
  MatchSummaryDto,
  LiveMatchDto,
  HeadToHeadDto,
  VsChampionDto,
  OpponentSummaryDto,
  ChampionMatchupSummaryDto,
  TeammateSummaryDto,
  LaneOpponentSummaryDto,
  RolesDto,
  TimeStatsDto,
  InsightsDto,
  MatchDetailDto,
  PerformanceTrend,
  CarryIndexDto,
  GameLengthStats,
  QueueAdvice,
  TimelineAnalysis,
  Page,
  ApiError,
} from './types';

const BASE = import.meta.env.VITE_API_BASE_URL ?? '/api';

/** Error carrying the parsed backend error envelope (when available). */
export class ApiRequestError extends Error {
  status: number;
  body?: ApiError;

  constructor(message: string, status: number, body?: ApiError) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.body = body;
  }
}

const enc = encodeURIComponent;

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      headers: { Accept: 'application/json' },
      ...init,
    });
  } catch (e) {
    throw new ApiRequestError(
      e instanceof Error ? e.message : 'Network request failed',
      0,
    );
  }

  const text = await res.text();
  let parsed: unknown = undefined;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = undefined;
    }
  }

  if (!res.ok) {
    const body = parsed as ApiError | undefined;
    const msg = body?.error ?? `Request failed with status ${res.status}`;
    throw new ApiRequestError(msg, res.status, body);
  }

  return parsed as T;
}

/** Path prefix for a given Riot ID, with both segments URL-encoded. */
function playerPath(gameName: string, tagLine: string): string {
  return `/players/${enc(gameName)}/${enc(tagLine)}`;
}

export function getPlayer(gameName: string, tagLine: string): Promise<PlayerDto> {
  return request<PlayerDto>(playerPath(gameName, tagLine));
}

export function getStats(
  gameName: string,
  tagLine: string,
  queue?: number,
): Promise<PlayerStatsDto> {
  const q = queue != null ? `?queue=${queue}` : '';
  return request<PlayerStatsDto>(`${playerPath(gameName, tagLine)}/stats${q}`);
}

export function getMatches(
  gameName: string,
  tagLine: string,
  page: number,
  size: number,
  queue?: number,
): Promise<Page<MatchSummaryDto>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (queue != null) params.set('queue', String(queue));
  return request<Page<MatchSummaryDto>>(
    `${playerPath(gameName, tagLine)}/matches?${params.toString()}`,
  );
}

export function getLive(gameName: string, tagLine: string): Promise<LiveMatchDto> {
  return request<LiveMatchDto>(`${playerPath(gameName, tagLine)}/live`);
}

export function getHeadToHead(
  gameName: string,
  tagLine: string,
  oppGameName: string,
  oppTagLine: string,
): Promise<HeadToHeadDto> {
  return request<HeadToHeadDto>(
    `${playerPath(gameName, tagLine)}/vs/${enc(oppGameName)}/${enc(oppTagLine)}`,
  );
}

export function getVsChampion(
  gameName: string,
  tagLine: string,
  championName: string,
): Promise<VsChampionDto> {
  return request<VsChampionDto>(
    `${playerPath(gameName, tagLine)}/vs-champion/${enc(championName)}`,
  );
}

export function getOpponents(
  gameName: string,
  tagLine: string,
  minGames = 2,
  limit = 200,
): Promise<OpponentSummaryDto[]> {
  const params = new URLSearchParams({
    minGames: String(minGames),
    limit: String(limit),
  });
  return request<OpponentSummaryDto[]>(
    `${playerPath(gameName, tagLine)}/opponents?${params.toString()}`,
  );
}

export function getChampionMatchups(
  gameName: string,
  tagLine: string,
  limit = 300,
): Promise<ChampionMatchupSummaryDto[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  return request<ChampionMatchupSummaryDto[]>(
    `${playerPath(gameName, tagLine)}/champion-matchups?${params.toString()}`,
  );
}

export function getTeammates(
  gameName: string,
  tagLine: string,
  minGames = 2,
  limit = 200,
): Promise<TeammateSummaryDto[]> {
  const params = new URLSearchParams({
    minGames: String(minGames),
    limit: String(limit),
  });
  return request<TeammateSummaryDto[]>(
    `${playerPath(gameName, tagLine)}/teammates?${params.toString()}`,
  );
}

export function getLaneOpponents(
  gameName: string,
  tagLine: string,
  limit = 200,
): Promise<LaneOpponentSummaryDto[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  return request<LaneOpponentSummaryDto[]>(
    `${playerPath(gameName, tagLine)}/lane-opponents?${params.toString()}`,
  );
}

export function getRoles(
  gameName: string,
  tagLine: string,
  queue?: number,
  champion?: string,
  role?: string,
): Promise<RolesDto> {
  const params = new URLSearchParams();
  if (queue != null) params.set('queue', String(queue));
  if (champion) params.set('champion', champion);
  if (role) params.set('role', role);
  const qs = params.toString();
  return request<RolesDto>(`${playerPath(gameName, tagLine)}/roles${qs ? `?${qs}` : ''}`);
}

/** tz is the JS timezone offset in minutes (Date.getTimezoneOffset()). */
export function getTimeStats(
  gameName: string,
  tagLine: string,
  tz: number,
  queue?: number,
  champion?: string,
  role?: string,
): Promise<TimeStatsDto> {
  const params = new URLSearchParams({ tz: String(tz) });
  if (queue != null) params.set('queue', String(queue));
  if (champion) params.set('champion', champion);
  if (role) params.set('role', role);
  return request<TimeStatsDto>(
    `${playerPath(gameName, tagLine)}/time?${params.toString()}`,
  );
}

export function getInsights(
  gameName: string,
  tagLine: string,
  tz: number,
  queue?: number,
): Promise<InsightsDto> {
  const params = new URLSearchParams({ tz: String(tz) });
  if (queue != null) params.set('queue', String(queue));
  return request<InsightsDto>(
    `${playerPath(gameName, tagLine)}/insights?${params.toString()}`,
  );
}

export function refresh(gameName: string, tagLine: string): Promise<PlayerDto> {
  return request<PlayerDto>(`${playerPath(gameName, tagLine)}/refresh`, {
    method: 'POST',
  });
}

export function getTracked(): Promise<PlayerDto[]> {
  return request<PlayerDto[]>('/tracked');
}

export function getMatchDetail(matchId: string): Promise<MatchDetailDto> {
  return request<MatchDetailDto>(`/matches/${enc(matchId)}`);
}

/** Add a player to the tracked set (registers, refreshes and backfills them). */
export function trackPlayer(gameName: string, tagLine: string): Promise<PlayerDto> {
  return request<PlayerDto>(`${playerPath(gameName, tagLine)}/track`, { method: 'POST' });
}

/** Remove a player from the tracked set (their stored data is kept). */
export function untrackPlayer(gameName: string, tagLine: string): Promise<PlayerDto> {
  return request<PlayerDto>(`${playerPath(gameName, tagLine)}/track`, { method: 'DELETE' });
}

export function getMatchAnalysis(matchId: string): Promise<TimelineAnalysis> {
  return request<TimelineAnalysis>(`/matches/${enc(matchId)}/analysis`);
}

/** Per-game performance series for the current season (optional queue filter). */
export function getPerformance(
  gameName: string,
  tagLine: string,
  queue?: number,
): Promise<PerformanceTrend> {
  const q = queue != null ? `?queue=${queue}` : '';
  return request<PerformanceTrend>(`${playerPath(gameName, tagLine)}/performance${q}`);
}

/** Carry Index: your share of your team's output this season (optional filters). */
export function getCarry(
  gameName: string,
  tagLine: string,
  queue?: number,
  champion?: string,
  role?: string,
): Promise<CarryIndexDto> {
  const params = new URLSearchParams();
  if (queue != null) params.set('queue', String(queue));
  if (champion) params.set('champion', champion);
  if (role) params.set('role', role);
  const qs = params.toString();
  return request<CarryIndexDto>(
    `${playerPath(gameName, tagLine)}/carry${qs ? `?${qs}` : ''}`,
  );
}

/** Win rate by game-length bucket this season (optional filters). */
export function getGameLength(
  gameName: string,
  tagLine: string,
  queue?: number,
  champion?: string,
  role?: string,
): Promise<GameLengthStats> {
  const params = new URLSearchParams();
  if (queue != null) params.set('queue', String(queue));
  if (champion) params.set('champion', champion);
  if (role) params.set('role', role);
  const qs = params.toString();
  return request<GameLengthStats>(
    `${playerPath(gameName, tagLine)}/game-length${qs ? `?${qs}` : ''}`,
  );
}

/** "Should I queue again?" live advice for the next game. tz is Date.getTimezoneOffset(). */
export function getQueueAdvice(
  gameName: string,
  tagLine: string,
  tz: number,
  queue?: number,
): Promise<QueueAdvice> {
  const params = new URLSearchParams({ tz: String(tz) });
  if (queue != null) params.set('queue', String(queue));
  return request<QueueAdvice>(
    `${playerPath(gameName, tagLine)}/queue-advice?${params.toString()}`,
  );
}
