import { Panel } from '../ui';
import {
  ROLES, TURRETS, WAVE_STATES, avgWave, killGold, plateHP, roamModel, verdictOf,
  type JudgeMode, type RoamOptions, type Situation, type TurretTier, type WaveState,
} from '../../lib/decision';
import { Select, Slider, fmt, gold, pctTxt, xp1 } from './controls';

export function RoamScenarioPanel({ s, o, onChange }: { s: Situation; o: RoamOptions; onChange: (patch: Partial<RoamOptions>) => void }) {
  const jungle = s.role === 'jungle';
  return (
    <Panel title="Roam scenario" sub="Defaults change with role — tune them to the play you’re considering">
      <div className="dm-grid-2">
        <Slider label="Time away from lane" value={o.duration} min={15} max={120} step={5} format={(v) => `${v}s`} onChange={(duration) => onChange({ duration })}
          hint={s.role === 'support' ? 'Atlas charges keep stacking for 60s — short roams cost little gold.' : 'Walk there + fight + walk back.'} />
        {jungle
          ? <Slider label="Seconds per camp if you farm instead" value={o.campSec} min={20} max={60} step={5} format={(v) => `${v}s`} onChange={(campSec) => onChange({ campSec })} hint="Includes walking between camps." />
          : <Select<WaveState> label="Your wave when you leave" value={o.wave} onChange={(wave) => onChange({ wave })} options={(Object.keys(WAVE_STATES) as WaveState[]).map((k) => ({ value: k, label: WAVE_STATES[k].label }))} />}
      </div>
      <div className="dm-grid-3">
        <Slider label="Takedown chance" value={Math.round(o.kill * 100)} min={0} max={100} step={5} format={(v) => `${v}%`} onChange={(v) => onChange({ kill: v / 100 })} />
        <Slider label="You get the kill" value={Math.round(o.credit * 100)} min={0} max={100} step={5} format={(v) => `${v}%`} onChange={(v) => onChange({ credit: v / 100 })} />
        <Slider label="You die" value={Math.round(o.death * 100)} min={0} max={100} step={5} format={(v) => `${v}%`} onChange={(v) => onChange({ death: v / 100 })} />
      </div>
      <Select<JudgeMode> label="Judge the roam by" value={o.mode} onChange={(mode) => onChange({ mode })}
        options={[{ value: 'team', label: 'Team value — your gain + allies’ gold/XP − what the enemy gets' }, { value: 'self', label: 'Personal value — only your own gold/XP' }]} />
      <div className="dm-grid-3">
        <Select label="Allies in the fight" value={o.allies} onChange={(allies) => onChange({ allies })} options={[1, 2, 3, 4].map((n) => ({ value: n, label: n === 1 ? '1 ally' : `${n} allies` }))} />
        <Select label="Plates you expect to lose" value={o.platesLost} onChange={(platesLost) => onChange({ platesLost })} options={[0, 1, 2, 3].map((n) => ({ value: n, label: n === 0 ? '0 — can’t punish' : `${n} plate${n > 1 ? 's' : ''}` }))} />
        <Select<TurretTier> label="Turret at risk" value={o.riskTier} onChange={(riskTier) => onChange({ riskTier })} options={(Object.keys(TURRETS) as TurretTier[]).map((k) => ({ value: k, label: TURRETS[k].name }))} />
      </div>
      <div className="dm-grid-3">
        <Slider label="Your CS rate" value={Math.round(o.csRate * 100)} min={50} max={100} step={5} format={(v) => `${v}%`} onChange={(v) => onChange({ csRate: v / 100 })} />
        <Select label="Value of 1 XP" value={o.xpValue} onChange={(xpValue) => onChange({ xpValue })} options={[0.5, 0.75, 1, 1.5].map((v) => ({ value: v, label: `${v}g${v === 1 ? ' · neutral' : v > 1 ? ' · level spike' : ''}` }))} />
        <label className="dm-field">
          <span className="dm-label">Follow-up gold on success</span>
          <input className="dm-input" type="number" min={0} max={2000} step={10} value={o.bonusGold} onChange={(e) => onChange({ bonusGold: Math.max(0, +e.target.value || 0) })} />
        </label>
      </div>
    </Panel>
  );
}

export function VerdictPanel({ s, o }: { s: Situation; o: RoamOptions }) {
  const m = roamModel(s.role, s.t, s.yourLevel, s.enemyLevel, o);
  const v = verdictOf(m);
  const tone = v === 'ROAM' ? 'roam' : v === 'FARM' ? 'farm' : 'flip';
  const crash = roamModel(s.role, s.t, s.yourLevel, s.enemyLevel, { ...o, wave: 'crash' });
  const neutral = roamModel(s.role, s.t, s.yourLevel, s.enemyLevel, { ...o, wave: 'neutral' });
  const shorter = roamModel(s.role, s.t, s.yourLevel, s.enemyLevel, { ...o, duration: Math.max(15, o.duration - 15) });
  const why: string[] = [];
  if (s.role !== 'jungle') why.push(`Crashing before you leave moves the break-even from ${pctTxt(neutral.breakEven)} (neutral) to ${pctTxt(crash.breakEven)}.`);
  why.push(`Each 15s shaved off the roam saves ≈ ${fmt(m.cost - shorter.cost, 0)} value. A wave arrives every ${m.interval}s right now.`);
  if (s.role === 'support') why.push(`While you’re away your ADC gets 100% of wave XP (+${xp1(avgWave('bot', s.t, false, false).xp - avgWave('bot', s.t, false, true).xp)} XP per wave).`);
  if (s.role === 'jungle') why.push(`Your farm alternative is ≈ ${gold(m.rate.gold * 60)} and ${xp1(m.rate.xp * 60)} XP per minute. Camps wait, so only half the gank time counts as lost.`);
  if (o.platesLost) why.push(`Losing ${o.platesLost} plate${o.platesLost > 1 ? 's' : ''} gives the enemy ≈ ${gold(m.plateLoss)}${o.mode === 'team' ? ' (counted)' : ' (ignored in personal mode)'}. Plate 1 needs only ${fmt(plateHP(o.riskTier, 0), 0)} damage.`);
  if (m.breakEven > 1) why.push('Even a guaranteed takedown would not pay this back. Shorten the roam, crash first, or add an objective.');

  return (
    <Panel title="Verdict" sub={`${ROLES[s.role].name} · level ${s.yourLevel} vs ${s.enemyLevel}`}>
      <div className={`dm-verdict ${tone}`}>
        <div className="dm-verdict-call">{v}</div>
        <p>
          Expected net {m.net >= 0 ? '+' : ''}{fmt(m.net, 0)} {o.mode === 'team' ? 'team' : 'personal'} value (gold + XP × {o.xpValue}).
          Personal break-even {pctTxt(m.selfBreakEven)} · team {pctTxt(m.teamBreakEven)}.
        </p>
      </div>
      <div className="tiles" style={{ marginTop: 12 }}>
        <div className="tile"><div className="tlabel">Farm you give up</div><div className="tvalue dm-gold">{gold(m.lost.gold)}</div><div className="tsub">{xp1(m.lost.xp)} XP · {s.role === 'jungle' ? `${fmt(m.lost.waves, 1)} camps` : `${fmt(m.lost.waves, 2)} waves`}</div></div>
        <div className="tile"><div className="tlabel">Expected roam gain</div><div className="tvalue dm-gold">+{gold(m.gain.gold)}</div><div className="tsub">+{xp1(m.gain.xp)} XP · kill {gold(m.killGoldYou)} / assist {gold(m.assistGoldYou)}</div></div>
        <div className="tile"><div className="tlabel">Expected death cost</div><div className="tvalue dm-gold">−{gold(m.deathCost.gold)}</div><div className="tsub">−{xp1(m.deathCost.xp)} XP · {Math.round(m.death.sec)}s off map · enemy gets {killGold(s.yourLevel)}g</div></div>
      </div>
      <div className="dm-be">
        <div className="spread note"><span>Your takedown chance <b className="dm-teal">{Math.round(o.kill * 100)}%</b></span><span>Break-even <b className="dm-gold">{pctTxt(m.breakEven)}</b></span></div>
        <div className="dm-be-track">
          <div className="dm-be-fill" style={{ width: `${Math.round(o.kill * 100)}%` }} />
          <div className="dm-be-mark" style={{ left: `calc(${Math.min(1, m.breakEven) * 100}% - 1px)` }} />
        </div>
      </div>
      <ul className="dm-why">{why.map((w) => <li key={w}>{w}</li>)}</ul>
    </Panel>
  );
}
