import type {
  PlayerDto,
  DiscoveredPageDto,
  FetchQueueDto,
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
  AuthStatus,
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

/** Fired when an API call comes back 401, so the auth state can re-check itself. */
export const AUTH_EXPIRED_EVENT = 'ra:auth-expired';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`;
  const hit = document.cookie.split('; ').find((c) => c.startsWith(prefix));
  return hit ? decodeURIComponent(hit.slice(prefix.length)) : undefined;
}

/**
 * The backend issues a CSRF token in a cookie on every response; it must be
 * echoed in a header on POST/PUT/DELETE. Make sure we have one before mutating.
 */
async function csrfToken(): Promise<string | undefined> {
  let token = readCookie(CSRF_COOKIE);
  if (!token) {
    await fetch(`${BASE}/auth/me`, { headers: { Accept: 'application/json' } }).catch(() => undefined);
    token = readCookie(CSRF_COOKIE);
  }
  return token;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? 'GET').toUpperCase();
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (init?.body != null) headers['Content-Type'] = 'application/json';
  if (!SAFE_METHODS.has(method)) {
    const token = await csrfToken();
    if (token) headers[CSRF_HEADER] = token;
  }

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      ...init,
      method,
      credentials: 'same-origin',
      headers: { ...headers, ...(init?.headers as Record<string, string> | undefined) },
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
    // A 401 anywhere but the login form means the admin session is gone.
    if (res.status === 401 && path !== '/auth/login') {
      window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
    }
    throw new ApiRequestError(msg, res.status, body);
  }

  return parsed as T;
}

// ---- Admin session ----

export function getAuthStatus(): Promise<AuthStatus> {
  return request<AuthStatus>('/auth/me');
}

export function login(username: string, password: string): Promise<AuthStatus> {
  return request<AuthStatus>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export function logout(): Promise<void> {
  return request<void>('/auth/logout', { method: 'POST' });
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
  queue?: number,
): Promise<HeadToHeadDto> {
  const q = queue != null ? `?queue=${queue}` : '';
  return request<HeadToHeadDto>(
    `${playerPath(gameName, tagLine)}/vs/${enc(oppGameName)}/${enc(oppTagLine)}${q}`,
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
  queue?: number,
): Promise<OpponentSummaryDto[]> {
  const params = new URLSearchParams({
    minGames: String(minGames),
    limit: String(limit),
  });
  if (queue != null) params.set('queue', String(queue));
  return request<OpponentSummaryDto[]>(
    `${playerPath(gameName, tagLine)}/opponents?${params.toString()}`,
  );
}

export function getChampionMatchups(
  gameName: string,
  tagLine: string,
  queue?: number,
  limit = 300,
): Promise<ChampionMatchupSummaryDto[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (queue != null) params.set('queue', String(queue));
  return request<ChampionMatchupSummaryDto[]>(
    `${playerPath(gameName, tagLine)}/champion-matchups?${params.toString()}`,
  );
}

export function getTeammates(
  gameName: string,
  tagLine: string,
  minGames = 2,
  limit = 200,
  queue?: number,
): Promise<TeammateSummaryDto[]> {
  const params = new URLSearchParams({
    minGames: String(minGames),
    limit: String(limit),
  });
  if (queue != null) params.set('queue', String(queue));
  return request<TeammateSummaryDto[]>(
    `${playerPath(gameName, tagLine)}/teammates?${params.toString()}`,
  );
}

export function getLaneOpponents(
  gameName: string,
  tagLine: string,
  queue?: number,
  limit = 200,
): Promise<LaneOpponentSummaryDto[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (queue != null) params.set('queue', String(queue));
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

/** Admin only. */
export function getTracked(): Promise<PlayerDto[]> {
  return request<PlayerDto[]>('/tracked');
}

/** Admin only: players discovered from tracked players' matches (paged, closest first). */
export function getDiscovered(page = 0, size = 50, q?: string): Promise<DiscoveredPageDto> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (q && q.trim()) params.set('q', q.trim());
  return request<DiscoveredPageDto>(`/discovered?${params.toString()}`);
}

/** Admin only: what the background scheduler is fetching now and next. */
export function getFetchQueue(limit = 15): Promise<FetchQueueDto> {
  return request<FetchQueueDto>(`/fetch-queue?limit=${limit}`);
}

export function getMatchDetail(matchId: string): Promise<MatchDetailDto> {
  return request<MatchDetailDto>(`/matches/${enc(matchId)}`);
}

/** Admin only: add a player to the tracked set (registers, refreshes and backfills them). */
export function trackPlayer(gameName: string, tagLine: string): Promise<PlayerDto> {
  return request<PlayerDto>(`${playerPath(gameName, tagLine)}/track`, { method: 'POST' });
}

/** Admin only: remove a player from the tracked set (their stored data is kept). */
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
