// Roam-vs-farm model: what you give up by leaving lane vs. what a roam is expected to return.
import { ATLAS, ROLE_KEYS, WAVE_STATES, type JudgeMode, type Role, type TurretTier, type WaveState } from './constants';
import {
  avgCamp, avgWave, clampN, farmRate, killGold, killXP, plateGold, respawnSeconds, waveInterval, xpMultiplier, xpToNext,
} from './economy';

export interface RoamOptions {
  /** Seconds away from lane (walk there + fight + walk back). */
  duration: number;
  wave: WaveState;
  /** Probabilities 0–1. */
  kill: number;
  credit: number;
  death: number;
  /** Allies in the fight besides you (1–4). */
  allies: number;
  platesLost: number;
  riskTier: TurretTier;
  csRate: number;
  /** Gold value of 1 XP. */
  xpValue: number;
  mode: JudgeMode;
  bonusGold: number;
  campSec: number;
  quest: boolean;
  partner: boolean;
}

export const DEFAULT_ROAM_OPTIONS: Omit<RoamOptions, 'duration' | 'kill' | 'credit' | 'death' | 'allies'> = {
  wave: 'crash', platesLost: 0, riskTier: 'outer', csRate: 0.85, xpValue: 1, mode: 'team', bonusGold: 0, campSec: 30, quest: false, partner: true,
};

export interface GoldXp { gold: number; xp: number }
export interface RoamResult {
  interval: number;
  rate: GoldXp;
  lost: GoldXp & { waves: number };
  xpTake: number;
  killGoldYou: number;
  assistGoldYou: number;
  takeGold: number;
  death: GoldXp & { sec: number };
  gain: GoldXp;
  deathCost: GoldXp;
  farmValue: number;
  takedownValue: number;
  deathValue: number;
  team: { take: number; death: number; farm: number };
  selfNet: number;
  selfBreakEven: number;
  teamNet: number;
  teamBreakEven: number;
  /** Net / break-even / cost in the selected judge mode. */
  net: number;
  breakEven: number;
  cost: number;
  plateLoss: number;
  adcXpGain: number;
  teamSwing: number;
}

export function roamModel(r: Role, t: number, yourLevel: number, enemyLevel: number, o: RoamOptions): RoamResult {
  const ctx = { quest: o.quest, partner: o.partner, csRate: o.csRate, campSec: o.campSec };
  const rate = farmRate(r, t, yourLevel, ctx), I = waveInterval(t), ws = WAVE_STATES[o.wave];
  const lost = { gold: 0, xp: 0, waves: 0 };
  if (r === 'jungle') {
    // Camps wait for you — only the delayed respawns are lost (counted at 50%).
    lost.gold = rate.gold * o.duration * 0.5; lost.xp = rate.xp * o.duration * 0.5; lost.waves = o.duration / o.campSec;
  } else {
    const waves = Math.max(0, o.duration - ws.free * I) / I + ws.extra;
    const w = avgWave(r, t, o.quest, o.partner, o.csRate);
    lost.waves = waves; lost.xp = waves * w.xp;
    // Atlas charges keep stacking (max 3 = 60s), so a support only loses gold after that.
    lost.gold = r === 'support' ? (Math.max(0, o.duration - ATLAS.maxCharges * ATLAS.chargeEvery) / ATLAS.chargeEvery) * ATLAS.goldPerCharge : waves * w.gold;
  }
  const others = o.allies;
  const botBonus = r === 'bot' && o.quest ? 50 : 0;
  const xpTake = killXP(yourLevel, enemyLevel, others + 1) * xpMultiplier(r, o.quest);
  const kg = killGold(enemyLevel) + botBonus;
  const ag = (killGold(enemyLevel) * 0.5) / Math.max(1, others) + botBonus;
  const takeGold = o.credit * kg + (1 - o.credit) * ag + o.bonusGold;
  const deathSec = respawnSeconds(yourLevel, t) + 25; // respawn + walk back
  const death = {
    gold: r === 'support' ? (Math.max(0, deathSec - ATLAS.maxCharges * ATLAS.chargeEvery) / ATLAS.chargeEvery) * ATLAS.goldPerCharge : rate.gold * deathSec,
    xp: rate.xp * deathSec,
    sec: deathSec,
  };
  const V = (g: number, x: number) => g + o.xpValue * x;
  const farmValue = V(lost.gold, lost.xp), takedownValue = V(takeGold, xpTake), deathValue = V(death.gold, death.xp);
  const plateLoss = o.platesLost * plateGold(o.riskTier, t);
  const adcXpGain = r === 'support' && o.partner ? (avgWave('bot', t, false, false).xp - avgWave('bot', t, false, true).xp) * lost.waves : 0;
  const allyTake = (1 - o.credit) * killGold(enemyLevel) + o.credit * (others > 0 ? killGold(enemyLevel) * 0.5 : 0) + o.xpValue * xpTake * others;
  const team = { take: takedownValue + allyTake, death: deathValue + killGold(yourLevel), farm: farmValue + plateLoss - o.xpValue * adcXpGain };
  const selfNet = o.kill * takedownValue - o.death * deathValue - farmValue;
  const selfBreakEven = takedownValue > 0 ? (farmValue + o.death * deathValue) / takedownValue : Infinity;
  const teamNet = o.kill * team.take - o.death * team.death - team.farm;
  const teamBreakEven = team.take > 0 ? Math.max(0, team.farm + o.death * team.death) / team.take : Infinity;
  const isTeam = o.mode === 'team';
  return {
    interval: I, rate, lost, xpTake, killGoldYou: kg, assistGoldYou: ag, takeGold, death,
    gain: { gold: o.kill * takeGold, xp: o.kill * xpTake },
    deathCost: { gold: o.death * death.gold, xp: o.death * death.xp },
    farmValue, takedownValue, deathValue, team, selfNet, selfBreakEven, teamNet, teamBreakEven,
    net: isTeam ? teamNet : selfNet,
    breakEven: isTeam ? teamBreakEven : selfBreakEven,
    cost: isTeam ? team.farm : farmValue,
    plateLoss, adcXpGain,
    teamSwing: o.kill * killGold(enemyLevel) * 1.5 - o.death * killGold(yourLevel) - plateLoss - lost.gold,
  };
}

export type Verdict = 'ROAM' | 'FARM' | 'COIN FLIP';
export function verdictOf(m: RoamResult): Verdict {
  const tolerance = Math.max(25, 0.15 * m.cost);
  return m.net > tolerance ? 'ROAM' : m.net < -tolerance ? 'FARM' : 'COIN FLIP';
}

// ---------------------------------------------------------------- estimated levels
const curves = new Map<Role, number[]>();
/** Typical level for a role at a game time, from ~85% XP uptake of waves or camps. */
export function estimatedLevel(r: Role, t: number): number {
  let arr = curves.get(r);
  if (!arr) {
    arr = [];
    let cum = 0, lvl = 1;
    const dt = 5;
    for (let s = 0; s <= 3600; s += dt) {
      const m = s / 60;
      if (r === 'jungle') { if (m >= 1.5) cum += (avgCamp(lvl, false).xp / 32) * dt * 0.85; }
      else if (m >= 55 / 60) cum += (avgWave(r, m, false, true, 1).xp / waveInterval(m)) * dt * 0.85;
      while (lvl < 18 && cum >= xpToNext(lvl)) { cum -= xpToNext(lvl); lvl++; }
      arr.push(lvl);
    }
    curves.set(r, arr);
  }
  return arr[clampN(Math.round((t * 60) / 5), 0, arr.length - 1)];
}

/** Break-even takedown chance across the game for each wave state (for the chart). */
export function breakEvenSeries(r: Role, o: RoamOptions, from = 2, to = 40, step = 0.5) {
  const rows: { t: number; crash: number; neutral: number; losing: number }[] = [];
  for (let t = from; t <= to + 1e-9; t += step) {
    const l = estimatedLevel(r, t);
    const be = (wave: WaveState) => Math.min(1, roamModel(r, t, l, l, { ...o, wave }).breakEven);
    rows.push({ t, crash: be('crash'), neutral: be('neutral'), losing: be('losing') });
  }
  return rows;
}

// ---------------------------------------------------------------- wave clock
export interface WaveSpawn { n: number; t: number; cannon: boolean }
let waveCache: WaveSpawn[] | null = null;
/** Wave spawn schedule: first wave 0:30, cannon every 3rd → every 2nd (14:00) → every wave (25:00). */
export function waveSchedule(): WaveSpawn[] {
  if (waveCache) return waveCache;
  const list: WaveSpawn[] = [];
  let t = 0.5, n = 1, since14 = 0;
  while (t <= 62) {
    let cannon: boolean;
    if (t < 14) cannon = n % 3 === 0;
    else if (t < 25) { since14++; cannon = since14 % 2 === 1; }
    else cannon = true;
    list.push({ n, t, cannon });
    t += waveInterval(t) / 60; n++;
  }
  return (waveCache = list);
}

/** Each role's own default scenario at a game time (for the role comparison table). */
export function roleSnapshot(r: Role, t: number, base: Pick<RoamOptions, 'xpValue' | 'mode'>, defaults: { duration: number; kill: number; credit: number; death: number; allies: number }) {
  const level = estimatedLevel(r, t);
  const o: RoamOptions = {
    ...DEFAULT_ROAM_OPTIONS, ...base,
    duration: defaults.duration, kill: defaults.kill / 100, credit: defaults.credit / 100, death: defaults.death / 100, allies: defaults.allies,
  };
  const m = roamModel(r, t, level, level, o);
  const perUnit = r === 'jungle' ? avgCamp(level, false) : avgWave(r, t, false, true, 0.85);
  const rate = farmRate(r, t, level, { quest: false, partner: true, csRate: 0.85, campSec: 30 });
  return { role: r, level, model: m, perUnit, perMinute: { gold: rate.gold * 60, xp: rate.xp * 60 } };
}
export const allRoleSnapshots = (t: number, base: Pick<RoamOptions, 'xpValue' | 'mode'>, getDefaults: (r: Role) => { duration: number; kill: number; credit: number; death: number; allies: number }) =>
  ROLE_KEYS.map((r) => roleSnapshot(r, t, base, getDefaults(r)));
