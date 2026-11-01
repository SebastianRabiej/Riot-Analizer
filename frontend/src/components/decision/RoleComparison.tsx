import { useMemo } from 'react';
import { Panel } from '../ui';
import { ROLES, allRoleSnapshots, toClock, type JudgeMode, type Role } from '../../lib/decision';
import { gold, pctTxt, xp1 } from './controls';

function readOf(be: number) {
  if (be < 0.3) return <span className="badge dm-badge-win">Roam-friendly</span>;
  if (be < 0.55) return <span className="badge dm-badge-blue">Situational</span>;
  return <span className="badge dm-badge-gold">Farm first</span>;
}

export function RoleComparison({ t, role, xpValue, mode, onPick }: { t: number; role: Role; xpValue: number; mode: JudgeMode; onPick: (r: Role) => void }) {
  const rows = useMemo(() => allRoleSnapshots(t, { xpValue, mode }, (r) => ROLES[r].roam), [t, xpValue, mode]);
  return (
    <>
      <Panel title={`All roles at ${toClock(t)}`} sub="Each role with its own default roam (crashed wave, usual duration and odds). Click a row to switch role.">
        <div className="table-scroll">
          <table className="tbl">
            <thead>
              <tr>
                <th>Role</th><th className="num">Est. level</th><th className="num">XP / wave</th><th className="num">Gold / wave</th><th className="num">Farm per min</th>
                <th className="num">Default roam</th><th className="num">Roam costs</th><th className="num">Takedown value</th><th className="num">Break-even</th><th>Read</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(({ role: r, level, model: m, perUnit, perMinute }) => (
                <tr key={r} className={`clickable ${r === role ? 'dm-sel' : ''}`} onClick={() => onPick(r)}>
                  <td><b>{ROLES[r].name}</b></td>
                  <td className="num">{level}</td>
                  <td className="num">{xp1(perUnit.xp)}{r === 'jungle' ? ' /camp' : ''}</td>
                  <td className="num">{gold(perUnit.gold)}{r === 'jungle' ? ' /camp' : ''}</td>
                  <td className="num">{gold(perMinute.gold)} · {Math.round(perMinute.xp)} XP</td>
                  <td className="num">{ROLES[r].roam.duration}s · {ROLES[r].roam.kill}%</td>
                  <td className="num">{gold(m.lost.gold)} · {Math.round(m.lost.xp)} XP</td>
                  <td className="num">{Math.round(m.takedownValue)}</td>
                  <td className="num">{pctTxt(m.breakEven)}</td>
                  <td>{readOf(m.breakEven)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>
      <div className="dm-playbooks">
        {rows.map(({ role: r, level, model: m }) => (
          <div key={r} className={`insight ${r === role ? 'good' : 'neutral'} dm-playbook`}>
            <div>
              <div className="i-title">{ROLES[r].icon} · {ROLES[r].name}</div>
              <div className="i-detail dm-teal">Now (~lvl {level}): a {ROLES[r].roam.duration}s roam costs ≈ {gold(m.lost.gold)} + {Math.round(m.lost.xp)} XP; break-even {pctTxt(m.breakEven)}.</div>
              <ul>{ROLES[r].playbook.map((p) => <li key={p}>{p}</li>)}</ul>
            </div>
          </div>
        ))}
      </div>
    </>
  );
}
