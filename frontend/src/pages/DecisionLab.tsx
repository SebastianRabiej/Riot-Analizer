import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Segmented } from '../components/ui';
import { SituationPanel } from '../components/decision/SituationPanel';
import { ActionValuePanel } from '../components/decision/ActionValuePanel';
import { RoamScenarioPanel, VerdictPanel } from '../components/decision/RoamPanels';
import { BreakEvenChart } from '../components/decision/BreakEvenChart';
import { RoleComparison } from '../components/decision/RoleComparison';
import { DEFAULT_ROAM_OPTIONS, PATCH, ROLES, ROLE_KEYS, type Role, type RoamOptions, type Situation } from '../lib/decision';

const isRole = (v: string | null): v is Role => !!v && (ROLE_KEYS as string[]).includes(v);
const lvl = (v: string | null, d: number) => { const n = Number(v); return Number.isInteger(n) && n >= 1 && n <= 18 ? n : d; };

function roamDefaults(r: Role): RoamOptions {
  const d = ROLES[r].roam;
  return { ...DEFAULT_ROAM_OPTIONS, duration: d.duration, kill: d.kill / 100, credit: d.credit / 100, death: d.death / 100, allies: d.allies };
}

/** Decision Lab: what every action is worth per role, and whether a roam beats farming. */
export function DecisionLab() {
  const [params, setParams] = useSearchParams();
  const role: Role = isRole(params.get('role')) ? (params.get('role') as Role) : 'mid';
  const tParam = Number(params.get('t'));
  const [flags, setFlags] = useState({ quest: false, partner: true });
  const s: Situation = {
    role,
    t: Number.isFinite(tParam) && tParam >= 0 && tParam <= 60 && params.get('t') !== null ? tParam : 8,
    yourLevel: lvl(params.get('lvl'), 6),
    enemyLevel: lvl(params.get('elvl'), lvl(params.get('lvl'), 6)),
    ...flags,
  };
  const [roam, setRoam] = useState<RoamOptions>(() => roamDefaults(role));
  const o: RoamOptions = { ...roam, quest: s.quest, partner: s.partner };

  const update = (patch: Partial<Situation>) => {
    const next = new URLSearchParams(params);
    if (patch.role) next.set('role', patch.role);
    if (patch.t !== undefined) next.set('t', String(Math.round(patch.t * 100) / 100));
    if (patch.yourLevel !== undefined) {
      next.set('lvl', String(patch.yourLevel));
      if (!params.get('elvl')) next.delete('elvl'); // opponent follows your level until edited
    }
    if (patch.enemyLevel !== undefined) next.set('elvl', String(patch.enemyLevel));
    setParams(next, { replace: true });
    if (patch.quest !== undefined || patch.partner !== undefined) setFlags((f) => ({ quest: patch.quest ?? f.quest, partner: patch.partner ?? f.partner }));
  };
  const pickRole = (r: Role) => { update({ role: r }); setRoam((cur) => ({ ...cur, ...roamDefaults(r), mode: cur.mode, xpValue: cur.xpValue, csRate: cur.csRate })); };

  return (
    <div className="stack dm-page">
      <div className="dm-head">
        <div>
          <div className="section-title" style={{ margin: 0 }}>Decision Lab · patch {PATCH}</div>
          <h1 className="dm-title">Roam or farm? <span className="dm-teal">Every action has a price.</span></h1>
          <p className="note" style={{ maxWidth: 680, marginTop: 6 }}>
            Pick a role to see what kills, assists, waves and plates are worth for it, then weigh a roam against the farm you would leave behind.
          </p>
        </div>
        <Segmented<Role> options={ROLE_KEYS.map((r) => ({ value: r, label: ROLES[r].name }))} value={role} onChange={pickRole} />
      </div>

      <div className="dm-cols">
        <SituationPanel s={s} onChange={update} />
        <ActionValuePanel s={s} />
      </div>

      <div className="section-title" style={{ marginBottom: 0 }}>Roam or farm your lane?</div>
      <div className="dm-cols">
        <RoamScenarioPanel s={s} o={o} onChange={(patch) => setRoam((cur) => ({ ...cur, ...patch }))} />
        <VerdictPanel s={s} o={o} />
      </div>
      <BreakEvenChart s={s} o={o} />
      <RoleComparison t={s.t} role={role} xpValue={o.xpValue} mode={o.mode} onPick={pickRole} />

      <p className="note">
        Estimates from patch {PATCH} rules: 0:30 first wave, 25s/20s waves after 14:00/30:00, minion XP share 100/65/43%, permanent outer plates
        (120g → 80g between 12:00 and 15:00), role quests. Inner/inhibitor plates assume an even 120g split of Riot’s 600g. Levels in the chart and
        role table are estimated from typical farm. First Blood, bounties, slows and Flash are not modelled.
      </p>
    </div>
  );
}
