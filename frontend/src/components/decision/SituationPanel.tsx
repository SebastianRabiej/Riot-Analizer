import { useEffect, useState } from 'react';
import { Panel } from '../ui';
import { ROLES, avgCamp, avgWave, cannonFreq, jungleMinionFactor, laneXPShare, parseClock, plateGold, toClock, waveInterval, type Situation } from '../../lib/decision';
import { Select, Toggle, gold, xp1 } from './controls';

const LEVELS = Array.from({ length: 18 }, (_, i) => ({ value: i + 1, label: `Level ${i + 1}` }));
const PRESETS = [4, 8, 12, 16, 20, 28];

export function SituationPanel({ s, onChange }: { s: Situation; onChange: (patch: Partial<Situation>) => void }) {
  const [clock, setClock] = useState(toClock(s.t));
  useEffect(() => setClock(toClock(s.t)), [s.t]);
  const R = ROLES[s.role];
  const w = avgWave(s.role, s.t, s.quest, s.partner);
  const facts = s.role === 'jungle'
    ? [['Camp XP (avg)', xp1(avgCamp(s.yourLevel, s.quest).xp)], ['Lane minion XP', `${Math.round(jungleMinionFactor(s.t) * 100)}%`], ['Wave interval', `${waveInterval(s.t)}s`]]
    : [['Minion XP share', `${Math.round(laneXPShare(s.role, s.partner) * 100)}%`], ['XP / avg wave', xp1(w.xp)], ['Gold / avg wave', gold(w.gold)], ['Wave interval', `${waveInterval(s.t)}s`], ['Cannon waves', `${Math.round(cannonFreq(s.t) * 100)}%`], ['Outer plate', gold(plateGold('outer', s.t))]];

  return (
    <Panel title="Situation" sub="Level and game time drive every number on this page">
      <div className="dm-grid-2">
        <Select label="Your level" value={s.yourLevel} options={LEVELS} onChange={(v) => onChange({ yourLevel: v })} />
        <Select label="Opponent level" value={s.enemyLevel} options={LEVELS} onChange={(v) => onChange({ enemyLevel: v })} />
      </div>
      <div className="dm-field" style={{ marginTop: 12 }}>
        <span className="dm-label">Game time <span className="note">mm:ss</span></span>
        <div className="row wrap">
          <input
            className={`dm-input dm-clock ${parseClock(clock) === null ? 'invalid' : ''}`}
            value={clock}
            inputMode="numeric"
            aria-label="Game time"
            onChange={(e) => { setClock(e.target.value); const v = parseClock(e.target.value); if (v !== null) onChange({ t: v }); }}
            onBlur={() => setClock(toClock(s.t))}
          />
          {PRESETS.map((m) => (
            <button key={m} type="button" className={`chip ${Math.abs(s.t - m) < 1e-6 ? 'active' : ''}`} onClick={() => onChange({ t: m })}>{m}m</button>
          ))}
        </div>
      </div>
      <div className="stack dm-toggles">
        {R.duo && (
          <Toggle checked={s.partner} onChange={(v) => onChange({ partner: v })} title="Lane partner in XP range"
            sub={s.role === 'support' ? 'You and your ADC split wave XP 65/65. Leaving lane gives your ADC 100%.' : 'You and your support split wave XP 65/65 instead of 100%.'} />
        )}
        <Toggle checked={s.quest} onChange={(v) => onChange({ quest: v })} title={R.quest.title} sub={R.quest.sub} />
      </div>
      <div className="streak-chips" style={{ marginTop: 14 }}>
        {facts.map(([k, v]) => <span key={k} className="streak-chip">{k} <b className="dm-teal">{v}</b></span>)}
      </div>
    </Panel>
  );
}
