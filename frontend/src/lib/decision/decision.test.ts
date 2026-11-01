import { describe, expect, it } from 'vitest';
import golden from './__fixtures__/golden.json';
import {
  avgCamp, avgWave, cannonFreq, cannonGold, estimatedLevel, jungleMinionFactor, killGold, killXP, parseClock, plateGold,
  plateHP, respawnSeconds, roamModel, roleWaveGold, roleWaveXP, toClock, verdictOf, waveGoldBase, waveInterval,
  waveSchedule, waveXPBase, type Role, type RoamOptions, type TurretTier, type WaveState, type JudgeMode,
} from './index';

// Golden values come from the standalone HTML calculator this feature was ported from
// ("Claude outputs/LoL_Role_XP_Gold_Roam_Calculator_v4.html"), rounded to 4 decimals.
const close = (a: number, b: number) => expect(a).toBeCloseTo(b, 3);

describe('waves, turrets and camps', () => {
  it('matches basics by game time', () => {
    for (const g of golden.basics) {
      expect(waveInterval(g.t)).toBe(g.interval);
      close(cannonFreq(g.t), g.cannonFreq);
      expect(cannonGold(g.t)).toBe(g.cannonGold);
      expect(plateGold('outer', g.t)).toBe(g.plateOuter);
      expect(plateGold('inner', g.t)).toBe(g.plateInner);
      close(jungleMinionFactor(g.t), g.jungleFactor);
      expect(waveXPBase('wave', g.t)).toBe(g.waveXP);
      expect(waveXPBase('cannon', g.t)).toBe(g.cannonXP);
      expect(waveGoldBase('wave', g.t)).toBe(g.waveGold);
      expect(waveGoldBase('cannon', g.t)).toBe(g.cannonWaveGold);
    }
  });
  it('matches plate HP per tier and plate', () => {
    (['outer', 'inner', 'inhib'] as TurretTier[]).forEach((tier, k) => golden.plateHP[k].forEach((hp, i) => close(plateHP(tier, i), hp)));
  });
  it('matches camp averages', () => {
    for (const c of golden.camps) {
      close(avgCamp(c.l, false).xp, c.avg.xp); close(avgCamp(c.l, true).gold, c.avgQ.gold);
    }
  });
  it('matches role wave XP and gold', () => {
    for (const w of golden.waves) {
      const r = w.r as Role;
      close(roleWaveXP(r, 'wave', w.t, w.q, w.p), w.wx);
      close(roleWaveXP(r, 'cannon', w.t, w.q, w.p), w.cx);
      close(roleWaveGold(r, 'wave', w.t, w.q, 1), w.wg);
      close(roleWaveGold(r, 'cannon', w.t, w.q, 0.85), w.cg);
      const a = avgWave(r, w.t, w.q, w.p, 0.85);
      close(a.xp, w.avg.xp); close(a.gold, w.avg.gold);
    }
  });
});

describe('champion rewards', () => {
  it('matches kill XP and gold for level gaps and sharing', () => {
    for (const k of golden.kills) { close(killXP(k.y, k.e, k.p), k.xp); expect(killGold(k.e)).toBe(k.gold); }
  });
  it('matches respawn timers', () => {
    for (const r of golden.respawn) close(respawnSeconds(r.l, r.t), r.v);
  });
});

describe('roam model', () => {
  it('matches estimated levels', () => {
    for (const row of golden.levels) {
      [1, 3, 5, 8, 10, 14, 20, 25, 30, 40].forEach((t, i) => expect(estimatedLevel(row.r as Role, t)).toBe(row.lv[i]));
    }
  });
  it('matches every golden roam scenario', () => {
    for (const g of golden.roam) {
      const o: RoamOptions = {
        duration: g.o.duration, wave: g.o.wave as WaveState, kill: g.o.kill, credit: g.o.credit, death: g.o.death, allies: g.o.allies,
        platesLost: g.o.plates, riskTier: g.o.riskTier as TurretTier, csRate: g.o.lh, xpValue: g.o.w, mode: g.o.mode as JudgeMode,
        bonusGold: g.o.bonus, campSec: g.o.campSec, quest: g.o.q, partner: g.o.p,
      };
      const m = roamModel(g.r as Role, g.t, g.y, g.e, o);
      close(m.lost.gold, g.lostGold); close(m.lost.xp, g.lostXp); close(m.lost.waves, g.lostWaves);
      close(m.xpTake, g.xpTake); close(m.killGoldYou, g.kg); close(m.assistGoldYou, g.ag);
      close(m.net, g.net); close(m.teamSwing, g.teamSwing);
      close(m.death.gold, g.deathGold); close(m.death.xp, g.deathXp); close(m.death.sec, g.deathSec);
      if (g.breakEven === null) expect(m.breakEven).toBe(Infinity); else close(m.breakEven, g.breakEven);
      if (g.selfBE !== null) close(m.selfBreakEven, g.selfBE);
      if (g.teamBE !== null) close(m.teamBreakEven, g.teamBE);
    }
  });
  it('gives sensible verdicts', () => {
    const base: RoamOptions = { duration: 40, wave: 'crash', kill: 0.45, credit: 0.5, death: 0.15, allies: 1, platesLost: 0, riskTier: 'outer', csRate: 0.85, xpValue: 1, mode: 'team', bonusGold: 0, campSec: 30, quest: false, partner: true };
    expect(verdictOf(roamModel('mid', 8, 7, 7, base))).toBe('ROAM');
    expect(verdictOf(roamModel('top', 14, 9, 9, { ...base, duration: 90, wave: 'losing', kill: 0.2 }))).toBe('FARM');
  });
});

describe('helpers', () => {
  it('formats and parses the game clock', () => {
    expect(toClock(8)).toBe('08:00'); expect(toClock(13.5)).toBe('13:30');
    expect(parseClock('12:30')).toBeCloseTo(12.5); expect(parseClock('7')).toBe(7);
    expect(parseClock('12:75')).toBeNull(); expect(parseClock('61')).toBeNull();
  });
  it('builds the wave schedule (first wave 0:30, cannon every 3rd wave early)', () => {
    const w = waveSchedule();
    expect(w[0]).toEqual({ n: 1, t: 0.5, cannon: false });
    expect(w[2].cannon).toBe(true);
    expect(w.filter((x) => x.t < 14).filter((x) => x.cannon).length).toBe(9);
  });
});

describe('action values', () => {
  const s = { role: 'mid' as Role, t: 8, yourLevel: 6, enemyLevel: 6, quest: false, partner: true };
  const assist = { others: 1, credited: 1 }, plate = { tier: 'outer' as TurretTier, index: 0, sharers: 1, firstTurret: false };
  it('values kills, assists and plates like the HTML calculator', async () => {
    const { actionReward, equivalents } = await import('./actions');
    expect(actionReward(s, 'solo', assist, plate)).toMatchObject({ xp: 234, gold: 300 });
    const a = actionReward(s, 'assist', assist, plate);
    close(a.xp, 77.922); expect(a.gold).toBe(150);
    expect(actionReward(s, 'plate', assist, { ...plate, index: 4, firstTurret: true }).gold).toBe(420);
    expect(actionReward({ ...s, t: 14 }, 'plate', assist, plate).gold).toBe(90);
    const eq = equivalents(s);
    expect(eq.find((x) => x.name === 'Three-person assist')).toMatchObject({ gold: 50 });
    expect(eq.filter((x) => x.group === 'Single turret plate').map((x) => x.gold)).toEqual([120, 120, 120]);
  });
});
