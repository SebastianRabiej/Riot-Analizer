// Pure economy functions (no React). Times are game minutes, levels 1–18.
import {
  ATLAS, CAMPS, JUNGLE_QUEST_BONUS, MINION_GOLD, MINION_XP, MONSTER_SCALE, PET_XP, PLATE_AT,
  RESPAWN, ROLES, SOLO_KILL_XP, TURRETS, XP_SHARE,
  type Camp, type Role, type TurretTier, type WaveType,
} from './constants';

export const clampN = (n: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, n));

/** XP needed to go from level l to l+1. */
export const xpToNext = (l: number) => 280 + 100 * (l - 1);

// ---------------------------------------------------------------- waves
/** Seconds between waves: 30s, 25s from 14:00, 20s from 30:00. */
export const waveInterval = (t: number) => (t < 14 ? 30 : t < 30 ? 25 : 20);
/** Share of waves that carry a cannon: every 3rd, every 2nd from 14:00, every wave from 25:00. */
export const cannonFreq = (t: number) => (t < 14 ? 1 / 3 : t < 25 ? 1 / 2 : 1);
export const cannonGold = (t: number) => 50 + Math.floor(Math.max(0, t - 0.5) / 1.5);
/** Jungle-item users get 30% → 100% lane-minion XP between 0:00 and 20:00. */
export const jungleMinionFactor = (t: number) => 0.3 + 0.7 * (clampN(t, 0, 20) / 20);

export interface WaveComposition { melee: number; caster: number; cannon: number; count: number; detail: string }
export function waveComposition(type: WaveType, t: number): WaveComposition {
  const cannon = type === 'cannon' ? 1 : 0;
  const caster = t >= 30 ? 2 : 3;
  const melee = cannon && t >= 14 ? 2 : 3;
  return { melee, caster, cannon, count: melee + caster + cannon, detail: `${melee} melee + ${caster} caster${cannon ? ' + 1 cannon' : ''}` };
}
export function waveXPBase(type: WaveType, t: number) {
  const c = waveComposition(type, t);
  return c.melee * MINION_XP.melee + c.caster * MINION_XP.caster + c.cannon * MINION_XP.cannon;
}
export function waveGoldBase(type: WaveType, t: number) {
  const c = waveComposition(type, t);
  return c.melee * MINION_GOLD.melee + c.caster * MINION_GOLD.caster + (c.cannon ? cannonGold(t) : 0);
}

/** Top-lane quest: +12.5% XP from all sources. */
export const xpMultiplier = (r: Role, quest: boolean) => (r === 'top' && quest ? 1.125 : 1);
/** Duo lanes share minion XP 65/65 when the partner is in range. */
export const laneXPShare = (r: Role, partner: boolean) => (ROLES[r].duo && partner ? XP_SHARE[1] : 1);

/** XP a role gets from one wave (XP is earned by proximity, independent of CS). */
export function roleWaveXP(r: Role, type: WaveType, t: number, quest: boolean, partner: boolean) {
  let xp = waveXPBase(type, t);
  xp *= r === 'jungle' ? jungleMinionFactor(t) : laneXPShare(r, partner);
  return xp * xpMultiplier(r, quest);
}
/** Gold a role gets from one wave at a given CS rate. Supports earn World Atlas executes instead. */
export function roleWaveGold(r: Role, type: WaveType, t: number, quest: boolean, csRate = 1) {
  if (r === 'support') return Math.min(ATLAS.maxCharges, waveInterval(t) / ATLAS.chargeEvery) * ATLAS.goldPerCharge;
  const c = waveComposition(type, t);
  let gold = waveGoldBase(type, t);
  if (r === 'bot' && quest) gold += 2 * c.count;
  return gold * csRate;
}
export function avgWave(r: Role, t: number, quest: boolean, partner: boolean, csRate = 1) {
  const f = cannonFreq(t);
  return {
    xp: (1 - f) * roleWaveXP(r, 'wave', t, quest, partner) + f * roleWaveXP(r, 'cannon', t, quest, partner),
    gold: (1 - f) * roleWaveGold(r, 'wave', t, quest, csRate) + f * roleWaveGold(r, 'cannon', t, quest, csRate),
  };
}

// ---------------------------------------------------------------- jungle camps
export const monsterScale = (l: number) => MONSTER_SCALE[clampN(l, 1, 8) - 1];
export const campXP = (c: Camp, level: number, quest: boolean) => c.base * monsterScale(level) + PET_XP + (quest ? JUNGLE_QUEST_BONUS : 0);
export const campGold = (c: Camp, quest: boolean) => c.gold + (quest ? JUNGLE_QUEST_BONUS : 0);
export function avgCamp(level: number, quest: boolean) {
  let xp = 0, gold = 0;
  for (const c of CAMPS) { xp += campXP(c, level, quest); gold += campGold(c, quest); }
  return { xp: xp / CAMPS.length, gold: gold / CAMPS.length };
}

export interface FarmContext { quest: boolean; partner: boolean; csRate: number; campSec: number }
/** Farm income per second for a role (jungle: camps; support: Atlas charges). */
export function farmRate(r: Role, t: number, level: number, o: FarmContext) {
  if (r === 'jungle') { const c = avgCamp(level, o.quest); return { xp: c.xp / o.campSec, gold: c.gold / o.campSec }; }
  const w = avgWave(r, t, o.quest, o.partner, o.csRate), I = waveInterval(t);
  return { xp: w.xp / I, gold: r === 'support' ? ATLAS.goldPerCharge / ATLAS.chargeEvery : w.gold / I };
}

// ---------------------------------------------------------------- champions
/** Base kill bounty: 300g at victim level 1–6, +10g per level after. */
export const killGold = (victimLevel: number) => 300 + Math.max(0, victimLevel - 6) * 10;
export const sharedKillModifier = (l: number) => (l <= 6 ? 0.666 : l <= 8 ? 0.82 : 0.9);
/** Kill XP for you. participants = everyone sharing the XP (1 = solo kill). */
export function killXP(yourLevel: number, victimLevel: number, participants: number) {
  const gap = Math.abs(yourLevel - victimLevel), diff = Math.max(0, gap - 1) * 0.2;
  const comeback = victimLevel > yourLevel ? 1 + diff : victimLevel < yourLevel ? Math.max(0.4, 1 - diff) : 1;
  const base = SOLO_KILL_XP[victimLevel - 1] * comeback;
  return participants === 1 ? base : (base * sharedKillModifier(yourLevel)) / participants;
}
/** Assist gold: half the base bounty, split between credited assisters. */
export const assistGold = (victimLevel: number, creditedAssisters: number) => (killGold(victimLevel) * 0.5) / Math.max(1, creditedAssisters);
export const respawnSeconds = (l: number, t: number) => RESPAWN[clampN(l, 1, 18) - 1] * (1 + Math.min(0.5, Math.max(0, t - 15) * 0.02));

// ---------------------------------------------------------------- turrets
/** Outer plates: 120g, −10g per minute from 12:00, 80g floor from 15:00. */
export const outerPlateGold = (t: number) => 120 - 10 * clampN(Math.floor(t) - 11, 0, 4);
/** Inner/inhibitor: Riot lists 600g over 5 plates — assumed an even 120g split. */
export const plateGold = (tier: TurretTier, t: number) => (TURRETS[tier].decay ? outerPlateGold(t) : 600 / 5);
export const plateHPShare = (i: number) => PLATE_AT[i] - (i ? PLATE_AT[i - 1] : 0);
export const plateHP = (tier: TurretTier, i: number) => TURRETS[tier].hp * plateHPShare(i);

// ---------------------------------------------------------------- clock
export function toClock(minutes: number) {
  const m = Math.floor(minutes), s = Math.round((minutes - m) * 60);
  return `${String(m + (s === 60 ? 1 : 0)).padStart(2, '0')}:${String(s === 60 ? 0 : s).padStart(2, '0')}`;
}
/** Parses "mm:ss" or plain minutes (0–60). Returns null when invalid. */
export function parseClock(input: string): number | null {
  const x = input.trim();
  if (/^\d{1,2}:\d{1,2}$/.test(x)) { const [m, s] = x.split(':').map(Number); if (s >= 60) return null; const v = m + s / 60; return v <= 60 ? v : null; }
  if (/^\d{1,2}(\.\d+)?$/.test(x)) { const v = Number(x); return v <= 60 ? v : null; }
  return null;
}
