// Game-economy constants for the Decision Maker.
// Every patch-dependent number lives here so a balance patch is a one-file change.
// Sources: Riot patch 26.1 notes (waves, minions, XP share, plates, role quests),
// League Wiki (kill XP, respawn), riftpatchnotes.com (World Atlas, turrets).

export const PATCH = '26.1+';

export type Role = 'top' | 'jungle' | 'mid' | 'bot' | 'support';
export const ROLE_KEYS: Role[] = ['top', 'jungle', 'mid', 'bot', 'support'];

export type WaveType = 'wave' | 'cannon';
export type WaveState = 'crash' | 'neutral' | 'losing';
export type TurretTier = 'outer' | 'inner' | 'inhib';
export type JudgeMode = 'team' | 'self';

/** Solo-kill XP by victim level 1–18. */
export const SOLO_KILL_XP = [42, 114, 144, 174, 204, 234, 308, 392, 486, 590, 640, 690, 740, 790, 840, 890, 940, 990];
/** Jungle monster XP multiplier by monster level 1–8+ (patch 14.10). */
export const MONSTER_SCALE = [1, 1, 1.25, 1.3, 1.35, 1.4, 1.45, 1.5];
export const MINION_XP = { melee: 62, caster: 31, cannon: 75 } as const;
export const MINION_GOLD = { melee: 20, caster: 14 } as const;
/** Minion XP share per champion when 1..6 champions are in range (patch 26.1). */
export const XP_SHARE = [1, 0.65, 0.433, 0.325, 0.26, 0.217];
/** Base respawn timer (s) by level 1–18, before the late-game scaling. */
export const RESPAWN = [10, 10, 12, 12, 14, 16, 20, 25, 28, 32.5, 35, 37.5, 40, 42.5, 45, 47.5, 50, 52.5];

export interface Camp { id: string; name: string; base: number; gold: number; icon: string }
/** Full-clear XP base (before monster scaling and +80 pet XP) and gold. */
export const CAMPS: Camp[] = [
  { id: 'gromp', name: 'Gromp', base: 120, gold: 80, icon: 'G' },
  { id: 'krugs', name: 'Krugs', base: 15 + 10 + 6 * 16, gold: 15 + 10 + 8 * 14, icon: 'K' },
  { id: 'wolves', name: 'Murk Wolves', base: 50 + 2 * 15, gold: 55 + 2 * 15, icon: 'W' },
  { id: 'raptors', name: 'Raptors', base: 20 + 5 * 10, gold: 35 + 5 * 8, icon: 'R' },
  { id: 'buff', name: 'Red / Blue Buff', base: 95, gold: 90, icon: 'B' },
];
export const PET_XP = 80;
export const JUNGLE_QUEST_BONUS = 10; // +10 XP and +10 gold per large monster

export const TURRETS: Record<TurretTier, { name: string; hp: number; decay: boolean }> = {
  outer: { name: 'Outer turret (T1)', hp: 9000, decay: true },
  inner: { name: 'Inner turret (T2)', hp: 5000, decay: false },
  inhib: { name: 'Inhibitor turret (T3)', hp: 4750, decay: false },
};
/** Plates break at these fractions of missing turret HP. */
export const PLATE_AT = [0.1, 0.25, 0.45, 0.7, 1];
export const FIRST_TURRET_BONUS = 300;

/** World Atlas (support item): gold per charge, one charge per 20 s, max 3. */
export const ATLAS = { goldPerCharge: 18, chargeEvery: 20, maxCharges: 3 } as const;

export const WAVE_STATES: Record<WaveState, { free: number; extra: number; label: string }> = {
  crash: { free: 1.2, extra: 0, label: 'Crashed into enemy tower' },
  neutral: { free: 0.4, extra: 0, label: 'Neutral / mid-lane' },
  losing: { free: 0, extra: 0.5, label: 'Pushing toward your tower' },
};

export interface RoamDefaults { duration: number; kill: number; credit: number; death: number; allies: number }
export interface RoleInfo {
  name: string;
  icon: string;
  tag: string;
  duo: boolean;
  roam: RoamDefaults;
  quest: { title: string; sub: string };
  playbook: string[];
}

export const ROLES: Record<Role, RoleInfo> = {
  top: {
    name: 'Top', icon: 'T', tag: 'Solo lane · long walks', duo: false,
    roam: { duration: 55, kill: 35, credit: 50, death: 15, allies: 2 },
    quest: { title: 'Top quest completed', sub: '+12.5% XP from all sources (applied), +600 XP once, level cap 20, Unleashed Teleport.' },
    playbook: [
      'Farm by default: lane points count double for the quest, and finishing it gives +600 XP, +12.5% XP and a level 20 cap.',
      'Roam only after crashing a cannon wave, or flank with Teleport into bot or dragon fights.',
      'From 14:00 waves come every 25s. Long walks then cost more than a wave, so prefer side-lane pressure.',
    ],
  },
  jungle: {
    name: 'Jungle', icon: 'J', tag: 'Camps · ganks', duo: false,
    roam: { duration: 35, kill: 40, credit: 40, death: 10, allies: 1 },
    quest: { title: 'Jungle quest completed', sub: '+10 XP and +10 gold per large monster (applied to camps), Smite 1400, +4% jungle/river move speed.' },
    playbook: [
      'Gank lanes pushed toward your laner, where the enemy has no escape (Flash down, low HP).',
      'Otherwise full clear: 35 camps completes the quest for +10 XP / +10g per large monster.',
      'Lane minions give you only 30% → 100% XP until 20:00, so don’t take waves early.',
    ],
  },
  mid: {
    name: 'Mid', icon: 'M', tag: 'Solo lane · shortest roams', duo: false,
    roam: { duration: 40, kill: 45, credit: 50, death: 15, allies: 1 },
    quest: { title: 'Mid quest completed', sub: 'Tier 3 boots + empowered 4s recall every 5 min (−1 min per takedown). No direct XP/gold change.' },
    playbook: [
      'The best roaming role: shortest paths to both sides and river.',
      'Roam right after shoving, ideally a cannon wave, at level 2/3/6 spikes, or when the enemy mid recalls.',
      'Quest: takedowns give 25 points and cut the empowered-recall cooldown by 1 min, so roams that work also reset faster.',
    ],
  },
  bot: {
    name: 'Bot (ADC)', icon: 'A', tag: 'Duo lane · gold hungry', duo: true,
    roam: { duration: 50, kill: 40, credit: 55, death: 15, allies: 2 },
    quest: { title: 'Bot quest completed', sub: '+2g per minion and +50g per takedown (applied), +300g once, boots move to a 7th slot.' },
    playbook: [
      'Farm first: the bot quest gives +2g per minion and +50g per takedown for the whole game.',
      'Rotate for dragon, or move mid after your outer turret falls.',
      'When your support roams you get 100% of wave XP instead of 65%. Play safe and farm through it.',
    ],
  },
  support: {
    name: 'Support', icon: 'S', tag: 'Duo lane · Atlas gold', duo: true,
    roam: { duration: 45, kill: 40, credit: 15, death: 20, allies: 1 },
    quest: { title: 'Support quest completed', sub: 'Control wards cost 40g and 2 can be stored in the quest slot. No direct XP/gold change.' },
    playbook: [
      'Cheapest roam in the game: Atlas charges stack to 3 (60s), and your ADC gets 100% of wave XP while you’re gone.',
      'Leave when your ADC can farm safely (wave crashed, or after both bot laners reset). Ward river on the way.',
      'Risk: your ADC is alone vs 2. Put that in the “plates you expect to lose” input.',
    ],
  },
};
