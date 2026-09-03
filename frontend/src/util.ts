// Small formatting + display helpers.

export const QUEUE_NAMES: Record<number, string> = {
  420: 'Ranked Solo/Duo',
  440: 'Ranked Flex',
  400: 'Normal Draft',
  430: 'Normal Blind',
  450: 'ARAM',
  700: 'Clash',
  490: 'Quickplay',
};

export interface QueueOption {
  label: string;
  queue?: number;
}

export const QUEUE_FILTERS: QueueOption[] = [
  { label: 'All' },
  { label: 'Solo 420', queue: 420 },
  { label: 'Flex 440', queue: 440 },
  { label: 'Normal 400', queue: 400 },
];

export function queueName(id: number, fallbackName?: string): string {
  return QUEUE_NAMES[id] ?? fallbackName ?? `Queue ${id}`;
}

/** Human "how long ago" from an epoch-ms timestamp. */
export function timeAgo(epochMs: number): string {
  const diffSec = Math.max(0, Math.floor((Date.now() - epochMs) / 1000));
  if (diffSec < 60) return `${diffSec}s ago`;
  const min = Math.floor(diffSec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}h ago`;
  const days = Math.floor(hr / 24);
  if (days < 30) return `${days}d ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months}mo ago`;
  return `${Math.floor(months / 12)}y ago`;
}

/** mm:ss from seconds. */
export function formatDuration(sec: number): string {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

export function formatKda(k: number, d: number, a: number): string {
  return `${k} / ${d} / ${a}`;
}

/** KDA ratio, guarding against divide-by-zero (perfect KDA). */
export function kdaRatio(kills: number, deaths: number, assists: number): number {
  if (deaths === 0) return kills + assists;
  return (kills + assists) / deaths;
}

export function round(n: number, digits = 1): number {
  const f = Math.pow(10, digits);
  return Math.round(n * f) / f;
}

export function formatNumber(n: number): string {
  return new Intl.NumberFormat('en-US').format(Math.round(n));
}

/** Parse a "gameName#tagLine" string into parts. Returns null when malformed. */
export function parseRiotId(
  raw: string,
): { gameName: string; tagLine: string } | null {
  const trimmed = raw.trim();
  const hash = trimmed.lastIndexOf('#');
  if (hash <= 0 || hash === trimmed.length - 1) return null;
  const gameName = trimmed.slice(0, hash).trim();
  const tagLine = trimmed.slice(hash + 1).trim();
  if (!gameName || !tagLine) return null;
  return { gameName, tagLine };
}

export function riotIdString(gameName: string, tagLine: string): string {
  return `${gameName}#${tagLine}`;
}

// ---------------------------------------------------------------------------
// Additional display helpers for the rebuilt UI.
// ---------------------------------------------------------------------------

/** Friendly, short label for a team position code. */
export function positionLabel(pos: string | null | undefined): string {
  switch ((pos ?? '').toUpperCase()) {
    case 'TOP': return 'Top';
    case 'JUNGLE': return 'Jungle';
    case 'MIDDLE': case 'MID': return 'Mid';
    case 'BOTTOM': case 'BOT': return 'Bot';
    case 'UTILITY': case 'SUPPORT': return 'Support';
    default: return 'Unknown';
  }
}

/** Emoji glyph standing in for a role (no external role-icon asset needed). */
export function positionIcon(pos: string | null | undefined): string {
  switch ((pos ?? '').toUpperCase()) {
    case 'TOP': return '⚔️';
    case 'JUNGLE': return '🌲';
    case 'MIDDLE': case 'MID': return '✨';
    case 'BOTTOM': case 'BOT': return '🏹';
    case 'UTILITY': case 'SUPPORT': return '🛡️';
    default: return '❔';
  }
}

/** Emoji emblem for a ranked tier. */
export function tierEmoji(tier: string | null | undefined): string {
  switch ((tier ?? '').toUpperCase()) {
    case 'IRON': return '🔩';
    case 'BRONZE': return '🥉';
    case 'SILVER': return '🥈';
    case 'GOLD': return '🥇';
    case 'PLATINUM': return '💠';
    case 'EMERALD': return '🟢';
    case 'DIAMOND': return '💎';
    case 'MASTER': return '🔮';
    case 'GRANDMASTER': return '👑';
    case 'CHALLENGER': return '🏆';
    default: return '🎯';
  }
}

/** Qualitative class for a win-rate percentage: good / mid / bad. */
export function wrClass(winRate: number): 'good' | 'mid' | 'bad' {
  if (winRate >= 53) return 'good';
  if (winRate < 48) return 'bad';
  return 'mid';
}

/** Percentage with one decimal and a % sign. */
export function pct(n: number): string {
  return `${round(n, 1)}%`;
}

/** Short absolute date, e.g. "12 Aug". */
export function dateShort(epochMs: number): string {
  return new Date(epochMs).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

export function clamp(n: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, n));
}
