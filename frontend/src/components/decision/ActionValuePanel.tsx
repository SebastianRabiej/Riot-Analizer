import { useMemo, useState } from 'react';
import { Panel, Segmented, StatTile } from '../ui';
import {
  ACTIONS, TURRETS, actionReward, equivalents, levelProgress, plateGold, plateHP, PLATE_AT,
  type ActionKind, type PlateSetup, type Situation, type TurretTier,
} from '../../lib/decision';
import { Select, Toggle, fmt, gold, xp1 } from './controls';

const ratio = (a: number, b: number) => (b > 0 ? `${fmt(a / b, 2)}×` : '—');

export function ActionValuePanel({ s }: { s: Situation }) {
  const [action, setAction] = useState<ActionKind>('solo');
  const [others, setOthers] = useState(1);
  const [credited, setCredited] = useState(1);
  const [plate, setPlate] = useState<PlateSetup>({ tier: 'outer', index: 0, sharers: 1, firstTurret: false });

  const creditedSafe = Math.min(credited, others);
  const res = actionReward(s, action, { others, credited: creditedSafe }, plate);
  const progress = levelProgress(res.xp, s.yourLevel, s.role, s.quest);
  const eq = useMemo(() => equivalents(s), [s]);
  const groups = [...new Set(eq.map((e) => e.group))];

  return (
    <Panel title="What is it worth?" sub="One action, valued for your role">
      <Segmented<ActionKind> options={ACTIONS.map((a) => ({ value: a.value, label: a.label }))} value={action} onChange={setAction} />

      {action === 'assist' && (
        <div className="dm-sub">
          <div className="dm-label">Other players sharing the XP <span className="note">killer + nearby allies</span></div>
          <Segmented<number> options={[1, 2, 3, 4].map((n) => ({ value: n, label: String(n) }))} value={others} onChange={setOthers} />
          <Select label="Players with an assist (you included) — split the assist gold" value={creditedSafe}
            options={Array.from({ length: others }, (_, i) => ({ value: i + 1, label: i === 0 ? '1 — only you' : `${i + 1} — you + ${i}` }))} onChange={setCredited} />
        </div>
      )}

      {action === 'plate' && (
        <div className="dm-sub">
          <div className="dm-grid-2">
            <Select<TurretTier> label="Turret" value={plate.tier} onChange={(tier) => setPlate({ ...plate, tier })}
              options={(Object.keys(TURRETS) as TurretTier[]).map((k) => ({ value: k, label: `${TURRETS[k].name} · ${TURRETS[k].hp.toLocaleString('en-US')} HP` }))} />
            <Select label="Allies sharing the gold" value={plate.sharers} onChange={(sharers) => setPlate({ ...plate, sharers })}
              options={[1, 2, 3, 4, 5].map((n) => ({ value: n, label: n === 1 ? '1 — only you' : `${n} players` }))} />
          </div>
          <div className="dm-label">Plate <span className="note">breaks at % of turret HP missing</span></div>
          <Segmented<number> options={PLATE_AT.map((p, i) => ({ value: i, label: `${i + 1} · ${Math.round(p * 100)}%` }))} value={plate.index} onChange={(index) => setPlate({ ...plate, index })} />
          {plate.index === 4 && <Toggle checked={plate.firstTurret} onChange={(firstTurret) => setPlate({ ...plate, firstTurret })} title="First turret of the game" sub="+300g split among nearby players when the turret dies." />}
          <div className="table-scroll">
            <table className="tbl dm-tbl-compact">
              <thead><tr><th>Plate</th><th className="num">HP to break</th><th className="num">Gold</th><th className="num">g / 1k dmg</th></tr></thead>
              <tbody>
                {PLATE_AT.map((_, i) => {
                  const pg = plateGold(plate.tier, s.t) + (i === 4 && plate.firstTurret ? 300 : 0), hp = plateHP(plate.tier, i);
                  return (
                    <tr key={i} className={i === plate.index ? 'dm-sel' : ''}>
                      <td>Plate {i + 1}{i === 4 ? ' · turret' : ''}</td><td className="num">{fmt(hp, 0)}</td><td className="num">{gold(pg)}</td><td className="num dm-gold">{gold((pg / hp) * 1000)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div className="tiles" style={{ marginTop: 14 }}>
        <StatTile label="Experience" value={<span className="dm-teal">{xp1(res.xp)} XP</span>} sub={progress === null ? 'Level cap' : `${fmt(progress * 100, 1)}% of the next level`} />
        <StatTile label="Gold" value={<span className="dm-gold">{gold(res.gold)}</span>} sub={res.goldDetail} />
      </div>
      <p className="note" style={{ marginTop: 10 }}>{res.description}</p>

      <div className="section-title" style={{ marginTop: 16 }}>That’s equivalent to</div>
      <div className="table-scroll">
        <table className="tbl dm-tbl-compact">
          <thead><tr><th>Compared with</th><th className="num">XP ×</th><th className="num">Gold ×</th></tr></thead>
          <tbody>
            {groups.map((g) => [
              <tr key={g} className="dm-group"><td colSpan={3}>{g}</td></tr>,
              ...eq.filter((e) => e.group === g).map((e) => (
                <tr key={g + e.name}>
                  <td><span className="dm-strong">{e.name}</span> <span className="note">{xp1(e.xp)} XP · {gold(e.gold)}{e.note ? ` · ${e.note}` : ''}</span></td>
                  <td className="num dm-teal">{ratio(res.xp, e.xp)}</td>
                  <td className="num dm-gold">{ratio(res.gold, e.gold)}</td>
                </tr>
              )),
            ])}
          </tbody>
        </table>
      </div>
    </Panel>
  );
}
