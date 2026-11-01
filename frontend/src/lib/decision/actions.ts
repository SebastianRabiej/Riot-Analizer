// Value of a single action (kill, assist, wave, plate…) for a role, plus "equivalent to" comparisons.
import { CAMPS, FIRST_TURRET_BONUS, ROLES, TURRETS, type Role, type TurretTier } from './constants';
import {
  assistGold, campGold, campXP, cannonFreq, farmRate, killGold, killXP, plateGold, plateHP, plateHPShare, roleWaveGold,
  roleWaveXP, sharedKillModifier, toClock, waveComposition, waveInterval, waveXPBase, xpMultiplier, xpToNext,
} from './economy';

export type ActionKind = 'solo' | 'assist' | 'wave' | 'cannon' | 'plate' | 'minute';
export const ACTIONS: { value: ActionKind; label: string; hint: string }[] = [
  { value: 'solo', label: 'Solo kill', hint: 'No XP-sharing allies' },
  { value: 'assist', label: 'Assist', hint: 'You assist; 1–4 others share XP' },
  { value: 'wave', label: 'Wave', hint: 'Without a cannon' },
  { value: 'cannon', label: 'Cannon wave', hint: 'With siege minion' },
  { value: 'plate', label: 'Turret plate', hint: 'Pick turret and plate' },
  { value: 'minute', label: '1 min farming', hint: 'Role-typical farm, perfect CS' },
];

export interface Situation { role: Role; t: number; yourLevel: number; enemyLevel: number; quest: boolean; partner: boolean }
export interface AssistSetup { others: number; credited: number }
export interface PlateSetup { tier: TurretTier; index: number; sharers: number; firstTurret: boolean }
export interface ActionReward { xp: number; gold: number; description: string; goldDetail: string }

const g0 = (n: number) => Math.round(n).toLocaleString('en-US');
const x1 = (n: number) => (Math.round(n * 10) / 10).toLocaleString('en-US');

export function actionReward(s: Situation, action: ActionKind, assist: AssistSetup, plate: PlateSetup): ActionReward {
  const { role, t, yourLevel: y, enemyLevel: e, quest, partner } = s;
  const R = ROLES[role], mult = xpMultiplier(role, quest), botQ = role === 'bot' && quest ? 50 : 0;
  switch (action) {
    case 'solo':
      return {
        xp: killXP(y, e, 1) * mult, gold: killGold(e) + botQ,
        description: `Solo kill on a level ${e} opponent${y === e ? ' (equal level)' : ''}. No XP sharing.`,
        goldDetail: `Normal kill reward: ${killGold(e)}g at victim level ${e}${botQ ? ' + 50g bot quest bonus' : ''}. Excludes First Blood and bounties.`,
      };
    case 'assist': {
      const parts = assist.others + 1, credited = Math.min(assist.credited, assist.others);
      return {
        xp: killXP(y, e, parts) * mult, gold: assistGold(e, credited) + botQ,
        description: `Assist on a level ${e} opponent: you + ${assist.others} other${assist.others > 1 ? 's' : ''} (${parts} total) share ${Math.round(sharedKillModifier(y) * 1000) / 10}% of solo-kill XP.`,
        goldDetail: `Assist gold: ${g0(killGold(e) * 0.5)}g pool ÷ ${credited} credited assister${credited > 1 ? 's' : ''}${botQ ? ' + 50g bot quest bonus' : ''}. The killer separately gets ${killGold(e)}g.`,
      };
    }
    case 'plate': {
      const T = TURRETS[plate.tier], pg = plateGold(plate.tier, t), first = plate.index === 4 && plate.firstTurret;
      const total = pg + (first ? FIRST_TURRET_BONUS : 0);
      return {
        xp: 0, gold: total / plate.sharers,
        description: `Plate ${plate.index + 1}/5 on the ${T.name}${plate.index === 4 ? ' — destroys the turret' : ''}. Needs ${g0(plateHP(plate.tier, plate.index))} damage (${Math.round(plateHPShare(plate.index) * 100)}% of its HP).`,
        goldDetail: `${T.decay ? `${pg}g at ${toClock(t)} (120g, −10g/min from 12:00, min 80g)` : `${pg}g (600g over 5 plates, no decay)`}${first ? ' + 300g first-turret bonus' : ''}${plate.sharers > 1 ? `, split ${plate.sharers} ways` : ''}. Plates give no XP.`,
      };
    }
    case 'minute': {
      const r = farmRate(role, t, y, { quest, partner, csRate: 1, campSec: 30 });
      return {
        xp: r.xp * 60, gold: r.gold * 60,
        description: role === 'jungle'
          ? `One minute of clearing at ~30s per camp (level ${y} monsters).`
          : `One minute of laning at ${toClock(t)}: ${(60 / waveInterval(t)).toFixed(2)} waves, ${Math.round(cannonFreq(t) * 100)}% with a cannon.`,
        goldDetail: role === 'support' ? 'World Atlas: 3 charges/min × 18g. Your ADC keeps the minion gold.' : `${g0(r.gold * 60)}g per minute with perfect last-hits.`,
      };
    }
    default: {
      const c = waveComposition(action, t), xp = roleWaveXP(role, action, t, quest, partner), gold = roleWaveGold(role, action, t, quest, 1);
      const share = role === 'jungle' ? ' × jungle minion penalty' : R.duo && partner ? ' × 65% duo share' : '';
      return {
        xp, gold,
        description: `${action === 'wave' ? 'Non-cannon' : 'Cannon'} wave (${c.detail}) at ${toClock(t)}: ${x1(waveXPBase(action, t))} base XP${share}${mult > 1 ? ' × 112.5% top quest' : ''}.`,
        goldDetail: role === 'support'
          ? `World Atlas executes ≈ ${g0(gold)}g per wave; your ADC still gets each minion's gold.`
          : `${g0(gold)}g from last-hitting all ${c.count} minions${role === 'bot' && quest ? ' incl. +2g/minion quest bonus' : ''}.`,
      };
    }
  }
}

export interface Equivalent { group: string; name: string; note: string; xp: number; gold: number }
export function equivalents(s: Situation): Equivalent[] {
  const { role, t, yourLevel: y, enemyLevel: e, quest, partner } = s;
  const mult = xpMultiplier(role, quest), botQ = role === 'bot' && quest ? 50 : 0;
  const out: Equivalent[] = [];
  if (role === 'jungle') {
    for (const c of CAMPS) out.push({ group: 'Full camp clears', name: c.name, note: '', xp: campXP(c, y, quest), gold: campGold(c, quest) });
  } else {
    const fr = farmRate(role, t, y, { quest, partner, csRate: 1, campSec: 30 });
    out.push(
      { group: 'Lane farm', name: 'Non-cannon wave', note: '', xp: roleWaveXP(role, 'wave', t, quest, partner), gold: roleWaveGold(role, 'wave', t, quest) },
      { group: 'Lane farm', name: 'Cannon wave', note: '', xp: roleWaveXP(role, 'cannon', t, quest, partner), gold: roleWaveGold(role, 'cannon', t, quest) },
      { group: 'Lane farm', name: '1 minute of laning', note: '', xp: fr.xp * 60, gold: fr.gold * 60 },
      { group: 'Kill', name: `Solo kill (level ${e})`, note: '', xp: killXP(y, e, 1) * mult, gold: killGold(e) + botQ },
    );
  }
  (['outer', 'inner', 'inhib'] as TurretTier[]).forEach((tier) =>
    out.push({ group: 'Single turret plate', name: TURRETS[tier].name.replace(' turret', ' plate'), note: TURRETS[tier].decay ? `at ${toClock(t)}` : '600g ÷ 5', xp: 0, gold: plateGold(tier, t) }));
  ([[1, 'Single assist', 'you + killer'], [2, 'Two-person assist', 'you + 1 assister + killer'], [3, 'Three-person assist', 'you + 2 assisters + killer']] as const).forEach(([n, name, who]) =>
    out.push({ group: `Assist · level ${e} victim`, name, note: who, xp: killXP(y, e, n + 1) * mult, gold: assistGold(e, n) + botQ }));
  return out;
}

/** Percent of the next level an amount of XP is worth (null at level 18 unless top quest). */
export function levelProgress(xp: number, level: number, role: Role, quest: boolean): number | null {
  if (level >= 18 && !(role === 'top' && quest)) return null;
  const need = level >= 18 ? 1980 : xpToNext(level); // 18→19 only exists with the top quest (estimated)
  return xp / need;
}
