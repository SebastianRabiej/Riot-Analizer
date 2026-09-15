import { useMemo, useState, type ReactNode } from 'react';
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, ReferenceLine } from 'recharts';
import type { TimelineParticipant, TimelineEvent } from '../types';
import { getMatchAnalysis } from '../api';
import { useAsync, useDdragonVersion, useItemData } from '../hooks';
import { Panel, Loading, ErrorBox, EmptyState, StatTile, Segmented } from './ui';
import { ChampionIcon } from './icons';
import { itemIconUrl } from '../ddragon';
import { MapObjectiveIcon, ObjectiveIcon, dragonElement, type ObjKind } from './objectiveIcons';
import { clamp, formatDuration, formatNumber, positionLabel, round } from '../util';

const MAP_MIN = -120;
const MAP_MAX = 14870;
const MAP_SIZE = 460;

function toXY(x: number, y: number) {
  const nx = (x - MAP_MIN) / (MAP_MAX - MAP_MIN);
  const ny = (y - MAP_MIN) / (MAP_MAX - MAP_MIN);
  return { cx: clamp(nx, 0, 1) * MAP_SIZE, cy: (1 - clamp(ny, 0, 1)) * MAP_SIZE };
}

function objKind(sub: string | null): ObjKind {
  const s = (sub ?? '').toUpperCase();
  if (s.includes('DRAGON')) return 'Dragon';
  if (s.includes('BARON')) return 'Baron';
  if (s.includes('HERALD')) return 'Herald';
  if (s.includes('HORDE') || s.includes('GRUB') || s.includes('VOID')) return 'Grubs';
  return 'Objective';
}

type MapEvent = { ts: number; x: number | null; y: number | null; cat: 'mine' | 'death' | 'ally' | 'enemy' | 'obj'; raw: TimelineEvent | null; obj: { kind: ObjKind; byMyTeam: boolean; element: string | null } | null };
const CAT_COLOR: Record<MapEvent['cat'], string> = { mine: 'var(--win)', death: 'var(--loss)', ally: 'var(--teal)', enemy: 'var(--warn)', obj: 'var(--gold)' };

function gradeColor(g: string): string {
  return g === 'S' ? 'var(--gold)' : g === 'A' ? 'var(--win)' : g === 'B' ? 'var(--teal)' : g === 'C' ? 'var(--warn)' : 'var(--loss)';
}
function sign(n: number): string {
  return `${n > 0 ? '+' : ''}${n}`;
}

export function MatchAnalysis({ matchId, mePuuid }: { matchId: string; mePuuid: string }) {
  const version = useDdragonVersion();
  const items = useItemData();
  const { data, loading, error } = useAsync(() => getMatchAnalysis(matchId), [matchId]);
  const [mapMode, setMapMode] = useState<'you' | 'all'>('you');
  const [hover, setHover] = useState<{ x: number; y: number; content: ReactNode } | null>(null);
  const [selectedDeath, setSelectedDeath] = useState<number | null>(null);
  const [curIdx, setCurIdx] = useState<number | null>(null);

  const d = useMemo(() => {
    if (!data) return null;
    const { participants, frames, events, frameIntervalMs } = data;
    const me = participants.find((p) => p.puuid === mePuuid);
    if (!me || frames.length === 0) return null;
    const meId = me.participantId;
    const myTeam = me.teamId;
    const byId = new Map<number, TimelineParticipant>(participants.map((p) => [p.participantId, p]));
    const teamOf = (pid: number | null) => (pid == null ? undefined : byId.get(pid)?.teamId);
    const opp = participants.find((p) => p.teamId !== myTeam && !!p.teamPosition && p.teamPosition === me.teamPosition) ?? null;

    const frameAt = (ms: number) => frames[Math.max(0, Math.min(frames.length - 1, Math.round(ms / frameIntervalMs)))];
    const pf = (frameIdx: number, pid: number) => frames[frameIdx].participants.find((p) => p.participantId === pid);
    const pfIn = (frame: (typeof frames)[number], pid: number) => frame.participants.find((p) => p.participantId === pid);

    const last = frames[frames.length - 1];
    const gameMin = last.timestampMs / 60000;

    const goldSeries = frames.map((f) => {
      let mine = 0, enemy = 0;
      for (const p of f.participants) (teamOf(p.participantId) === myTeam ? (mine += p.totalGold) : (enemy += p.totalGold));
      const mp = pfIn(f, meId);
      const op = opp ? pfIn(f, opp.participantId) : undefined;
      return { min: Math.round(f.timestampMs / 60000), teamDiff: mine - enemy, laneDiff: mp && op ? mp.totalGold - op.totalGold : null };
    });

    const kills = events.filter((e) => e.type === 'CHAMPION_KILL');
    let k = 0, dd = 0, a = 0, solo = 0, teamKills = 0;
    for (const e of kills) {
      if (teamOf(e.killerId) === myTeam) teamKills++;
      if (e.killerId === meId) { k++; if (!e.assistIds || e.assistIds.length === 0) solo++; }
      if (e.victimId === meId) dd++;
      if (e.assistIds?.includes(meId)) a++;
    }
    const kp = teamKills > 0 ? ((k + a) / teamKills) * 100 : 0;
    const kda = dd === 0 ? k + a : (k + a) / dd;

    let myGold = 0, teamGold = 0, myDmg = 0, teamDmg = 0;
    for (const p of last.participants) {
      const t = teamOf(p.participantId);
      if (p.participantId === meId) { myGold = p.totalGold; myDmg = p.damage; }
      if (t === myTeam) { teamGold += p.totalGold; teamDmg += p.damage; }
    }
    const oppEndGold = opp ? (last.participants.find((p) => p.participantId === opp.participantId)?.totalGold ?? 0) : 0;
    const goldShare = teamGold > 0 ? (myGold / teamGold) * 100 : 0;
    const dmgShare = teamDmg > 0 ? (myDmg / teamDmg) * 100 : 0;
    const finalCs = last.participants.find((p) => p.participantId === meId)?.cs ?? 0;
    const csPerMin = gameMin > 0 ? finalCs / gameMin : 0;
    const dmgPerMin = gameMin > 0 ? myDmg / gameMin : 0;

    let wardsPlaced = 0, wardsKilled = 0, controlWards = 0;
    for (const e of events) {
      if (e.type === 'WARD_PLACED' && e.participantId === meId) { wardsPlaced++; if (e.wardType === 'CONTROL_WARD') controlWards++; }
      if (e.type === 'WARD_KILL' && e.participantId === meId) wardsKilled++;
    }

    const levelTime = (n: number) => {
      const ev = events.find((e) => e.type === 'LEVEL_UP' && e.participantId === meId && e.level === n);
      return ev ? ev.timestampMs / 60000 : null;
    };
    const lvl6 = levelTime(6);

    const laneAt = (ms: number) => {
      if (!opp) return null;
      const idx = Math.max(0, Math.min(frames.length - 1, Math.round(ms / frameIntervalMs)));
      const mp = pf(idx, meId), op = pf(idx, opp.participantId);
      if (!mp || !op) return null;
      return { cs: mp.cs - op.cs, gold: mp.totalGold - op.totalGold, xp: mp.xp - op.xp, meCs: mp.cs, oppCs: op.cs };
    };
    const lane10 = laneAt(600000), lane14 = laneAt(840000);

    const deaths = kills
      .filter((e) => e.victimId === meId)
      .map((e) => {
        const f = frameAt(e.timestampMs);
        const myP = pfIn(f, meId);
        let mates = 0;
        if (myP) for (const p of f.participants) {
          if (p.participantId !== meId && teamOf(p.participantId) === myTeam) {
            if (Math.hypot(p.x - myP.x, p.y - myP.y) < 1800) mates++;
          }
        }
        return {
          min: e.timestampMs / 60000,
          x: e.x, y: e.y,
          killer: byId.get(e.killerId ?? -1)?.championName ?? '?',
          shutdown: e.shutdownBounty ?? 0,
          alone: myP ? mates === 0 : false,
          ts: e.timestampMs,
        };
      })
      .sort((p, q) => p.ts - q.ts);

    const objectives = events
      .filter((e) => e.type === 'ELITE_MONSTER_KILL')
      .map((e) => {
        const mine = e.teamId === myTeam;
        const f = frameAt(e.timestampMs);
        const myP = pfIn(f, meId);
        let present = false;
        if (mine && myP && e.x != null && e.y != null) present = Math.hypot(e.x - myP.x, e.y - myP.y) < 2600;
        return { ts: e.timestampMs, min: e.timestampMs / 60000, kind: objKind(e.subType), element: dragonElement(e.subType), byMyTeam: mine, present, x: e.x, y: e.y };
      });

    const champName = (pid: number | null | undefined) => (pid == null ? '?' : byId.get(pid)?.championName ?? '?');

    const buildBacks = (pid: number) => {
      const evs = events
        .filter((e) => (e.type === 'ITEM_PURCHASED' || e.type === 'ITEM_UNDO') && e.participantId === pid)
        .sort((x, y) => x.timestampMs - y.timestampMs);
      const buys: { t: number; item: number }[] = [];
      for (const e of evs) {
        if (e.type === 'ITEM_PURCHASED' && e.itemId != null) buys.push({ t: e.timestampMs, item: e.itemId });
        else if (e.type === 'ITEM_UNDO') buys.pop();
      }
      const myDeaths = events.filter((e) => e.type === 'CHAMPION_KILL' && e.victimId === pid);
      const deadInfo = (t: number): TimelineEvent | null => {
        let best: TimelineEvent | null = null;
        for (const e of myDeaths) {
          const dt = t - e.timestampMs;
          if (dt >= 0 && dt <= 70000 && (!best || e.timestampMs > best.timestampMs)) best = e;
        }
        return best;
      };

      // Pocket + earned gold just BEFORE a shop visit: take the frame at/just
      // before the purchase and add the gold earned in the seconds up to it
      // (interpolated from the next frame's totalGold), so "held" reflects what
      // the player walked into the shop with — not the post-purchase leftover.
      const goldAt = (t: number) => {
        const n = frames.length;
        const fi = clamp(Math.floor(t / frameIntervalMs), 0, n - 1);
        const cur = frames[fi].participants.find((p) => p.participantId === pid);
        if (!cur) return { total: 0, held: 0 };
        let held = cur.currentGold;
        let total = cur.totalGold;
        if (fi + 1 < n) {
          const nx = frames[fi + 1].participants.find((p) => p.participantId === pid);
          if (nx) {
            const frac = clamp((t - frames[fi].timestampMs) / frameIntervalMs, 0, 1);
            const earned = Math.max(0, nx.totalGold - cur.totalGold);
            held += earned * frac;
            total += earned * frac;
          }
        }
        return { total: Math.round(total), held: Math.round(held) };
      };
      const backs: { t: number; items: number[]; dead: boolean; killer: string | null; deathTs: number | null; totalGold: number; heldGold: number }[] = [];
      let lastT = -1e9;
      for (const b of buys) {
        if (b.t - lastT <= 12000 && backs.length) {
          backs[backs.length - 1].items.push(b.item);
        } else {
          const g = goldAt(b.t);
          const de = deadInfo(b.t);
          backs.push({ t: b.t, items: [b.item], dead: !!de, killer: de ? champName(de.killerId) : null, deathTs: de ? de.timestampMs : null, totalGold: g.total, heldGold: g.held });
        }
        lastT = b.t;
      }
      return backs;
    };
    const myBacks = buildBacks(meId);
    const oppBacks = opp ? buildBacks(opp.participantId) : [];

    let myPeakGold = 0, myPeakMin = 0, oppPeakGold = 0;
    for (let i = 1; i < frames.length; i++) {
      const mp = frames[i].participants.find((p) => p.participantId === meId);
      if (mp && mp.currentGold > myPeakGold) { myPeakGold = mp.currentGold; myPeakMin = Math.round(frames[i].timestampMs / 60000); }
      if (opp) {
        const op = frames[i].participants.find((p) => p.participantId === opp.participantId);
        if (op && op.currentGold > oppPeakGold) oppPeakGold = op.currentGold;
      }
    }

    let score = 0;
    score += Math.min(30, kp * 0.35);
    score += Math.min(25, kda * 6);
    score += (lane10 ? clamp(lane10.cs, -20, 20) + 20 : 20) * 0.4;
    score += Math.min(15, dmgShare * 0.6);
    score += Math.min(14, (wardsPlaced + wardsKilled) * 0.7);
    const grade = score >= 82 ? 'S' : score >= 70 ? 'A' : score >= 55 ? 'B' : score >= 40 ? 'C' : 'D';

    return {
      me, opp, meId, myTeam, byId, teamOf, goldSeries, kills, k, d: dd, a, solo, kp, kda,
      goldShare, dmgShare, csPerMin, dmgPerMin, finalCs, wardsPlaced, wardsKilled, controlWards,
      lvl6, lane10, lane14, deaths, objectives, gameMin, gameEndMs: last.timestampMs, grade,
      champName, myBacks, oppBacks, myPeakGold, myPeakMin, oppPeakGold,
      myEndGold: myGold, oppEndGold,
    };
  }, [data, mePuuid]);

  const timelineEvents = useMemo<MapEvent[]>(() => {
    if (!d) return [];
    const out: MapEvent[] = [];
    const pool = mapMode === 'you'
      ? d.kills.filter((e) => e.killerId === d.meId || e.victimId === d.meId)
      : d.kills;
    for (const e of pool) {
      let cat: MapEvent['cat'];
      if (e.killerId === d.meId) cat = 'mine';
      else if (e.victimId === d.meId) cat = 'death';
      else if (d.teamOf(e.killerId) === d.myTeam) cat = 'ally';
      else cat = 'enemy';
      out.push({ ts: e.timestampMs, x: e.x, y: e.y, cat, raw: e, obj: null });
    }
    for (const o of d.objectives) {
      out.push({ ts: o.ts, x: o.x, y: o.y, cat: 'obj', raw: null, obj: { kind: o.kind, byMyTeam: o.byMyTeam, element: o.element } });
    }
    out.sort((p, q) => p.ts - q.ts);
    return out;
  }, [d, mapMode]);

  if (loading && !data) return <Loading label="Fetching match timeline…" />;
  if (error) return <ErrorBox message={`Couldn't load timeline: ${error}`} />;
  if (!d) return <EmptyState emoji="🧩" message="Timeline analysis unavailable for this match" hint="Some very old or special-mode games don't expose a timeline." />;

  const mmss = (ms: number) => formatDuration(Math.floor(ms / 1000));
  const killLabel = (e: TimelineEvent) => {
    const assists = (e.assistIds ?? []).filter((id) => id !== d.meId).map((id) => d.champName(id));
    const died = e.victimId === d.meId;
    const mine = e.killerId === d.meId;
    const head = died
      ? `Killed by ${d.champName(e.killerId)}`
      : mine
        ? `You killed ${d.champName(e.victimId)}`
        : `${d.champName(e.killerId)} → ${d.champName(e.victimId)}`;
    return (
      <>
        <div><b>{mmss(e.timestampMs)}</b> · {head}</div>
        {assists.length > 0 && <div className="note">assist: {assists.join(', ')}</div>}
        {(e.shutdownBounty ?? 0) > 0 && <div className="note">{e.shutdownBounty}g shutdown</div>}
      </>
    );
  };

  const mapUrl = `https://ddragon.leagueoflegends.com/cdn/${version}/img/map/map11.png`;

  const jumpToMapDeath = (idx: number) => {
    setSelectedDeath(idx);
    setCurIdx(null);
    setMapMode('you');
    document.getElementById('kill-map')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };
  const selDeath = selectedDeath != null && d.deaths[selectedDeath] ? d.deaths[selectedDeath] : null;
  const curEvt = curIdx != null && timelineEvents[curIdx] ? timelineEvents[curIdx] : null;
  const endMs = Math.max(d.gameEndMs, timelineEvents.length ? timelineEvents[timelineEvents.length - 1].ts : 0) || 1;
  const mySide = d.myTeam === 100 ? 'Blue' : 'Red';
  const mySideColor = d.myTeam === 100 ? 'var(--blue)' : 'var(--loss)';
  const sidePoly = d.myTeam === 100 ? `0,0 0,${MAP_SIZE} ${MAP_SIZE},${MAP_SIZE}` : `0,0 ${MAP_SIZE},0 ${MAP_SIZE},${MAP_SIZE}`;
  const selectEvent = (i: number) => {
    if (i < 0 || i >= timelineEvents.length) return;
    setSelectedDeath(null);
    setCurIdx(i);
    requestAnimationFrame(() => document.getElementById(`ev-row-${i}`)?.scrollIntoView({ block: 'nearest' }));
  };
  const evActor = (ev: MapEvent) => (ev.raw ? d.champName(ev.raw.killerId) : '?');
  const evShort = (ev: MapEvent) => {
    if (ev.obj) return <><span className="ev-strong">{ev.obj.kind}</span> · {ev.obj.byMyTeam ? 'your team' : 'enemy team'}</>;
    const e = ev.raw!;
    if (ev.cat === 'mine') return <>You killed <span className="ev-strong">{d.champName(e.victimId)}</span></>;
    if (ev.cat === 'death') return <>Killed by <span className="ev-strong">{d.champName(e.killerId)}</span></>;
    return <><span className="ev-strong">{d.champName(e.killerId)}</span> ▸ {d.champName(e.victimId)}</>;
  };
  const objectiveTip = (kind: ObjKind, element: string | null, byMyTeam: boolean, ts: number): ReactNode => (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
        <b>{mmss(ts)}</b> · <ObjectiveIcon kind={kind} element={element} size={16} /> <span>{kind}</span>
      </div>
      <div className="note">secured by {byMyTeam ? 'your team' : 'the enemy team'}</div>
    </>
  );
  const eventTip = (ev: MapEvent): ReactNode => (ev.obj ? objectiveTip(ev.obj.kind, ev.obj.element, ev.obj.byMyTeam, ev.ts) : killLabel(ev.raw!));
  const evDesc = (ev: MapEvent) => eventTip(ev);
  const dragCount = d.objectives.filter((o) => o.kind === 'Dragon' && o.byMyTeam);
  const objSummary = ['Dragon', 'Herald', 'Baron', 'Grubs']
    .map((kind) => {
      const mineList = d.objectives.filter((o) => o.kind === kind && o.byMyTeam);
      if (mineList.length === 0) return null;
      const present = mineList.filter((o) => o.present).length;
      return { kind, present, total: mineList.length };
    })
    .filter(Boolean) as { kind: string; present: number; total: number }[];

  return (
    <div className="stack">
      <div className="dash">
        {/* Kill / death map */}
        <Panel
          title="Kill map"
          sub="Your kills (green) and deaths (red), with objectives"
          action={<Segmented options={[{ value: 'you', label: 'Your K/D' }, { value: 'all', label: 'All kills' }]} value={mapMode} onChange={(v) => { setMapMode(v); setCurIdx(null); }} />}
        >
          <div className="killmap-layout">
          <div className="riftmap" id="kill-map">
            <svg viewBox={`0 0 ${MAP_SIZE} ${MAP_SIZE}`} width="100%" style={{ display: 'block', borderRadius: 10 }}>
              <image href={mapUrl} x={0} y={0} width={MAP_SIZE} height={MAP_SIZE} preserveAspectRatio="xMidYMid slice" opacity={0.85} />
              <rect x={0} y={0} width={MAP_SIZE} height={MAP_SIZE} fill="none" stroke="var(--border-2)" rx={10} />
              <polygon points={sidePoly} fill={mySideColor} opacity={0.09} style={{ pointerEvents: 'none' }} />
              <text x={9} y={MAP_SIZE - 8} textAnchor="start" fontSize={11} fontWeight={800} fill="var(--blue)" stroke="#0b0d12" strokeWidth={3} paintOrder="stroke" style={{ pointerEvents: 'none' }}>{d.myTeam === 100 ? 'YOUR BASE' : 'ENEMY'}</text>
              <text x={MAP_SIZE - 9} y={16} textAnchor="end" fontSize={11} fontWeight={800} fill="var(--loss)" stroke="#0b0d12" strokeWidth={3} paintOrder="stroke" style={{ pointerEvents: 'none' }}>{d.myTeam === 200 ? 'YOUR BASE' : 'ENEMY'}</text>
              {mapMode === 'all' && d.kills.filter((e) => e.x != null && e.y != null).map((e, i) => {
                const { cx, cy } = toXY(e.x!, e.y!);
                const mineKill = d.teamOf(e.killerId) === d.myTeam;
                return <circle key={`a${i}`} cx={cx} cy={cy} r={3.5} fill={mineKill ? 'var(--teal)' : 'var(--warn)'} opacity={0.62} style={{ cursor: 'pointer' }} onMouseEnter={() => setHover({ x: cx, y: cy, content: killLabel(e) })} onMouseLeave={() => setHover(null)} />;
              })}
              {d.objectives.filter((o) => o.x != null && o.y != null).map((o, i) => {
                const { cx, cy } = toXY(o.x!, o.y!);
                return (
                  <g key={`o${i}`} style={{ cursor: 'pointer' }} onMouseEnter={() => setHover({ x: cx, y: cy, content: objectiveTip(o.kind, o.element, o.byMyTeam, o.ts) })} onMouseLeave={() => setHover(null)}>
                    <MapObjectiveIcon cx={cx} cy={cy} size={19} kind={o.kind} element={o.element} />
                    <circle cx={cx} cy={cy} r={11} fill="transparent" />
                  </g>
                );
              })}
              {mapMode === 'you' && d.kills.filter((e) => e.killerId === d.meId && e.x != null).map((e, i) => {
                const { cx, cy } = toXY(e.x!, e.y!);
                return <circle key={`k${i}`} cx={cx} cy={cy} r={6} fill="var(--win)" stroke="#0b0d12" strokeWidth={1.5} style={{ cursor: 'pointer' }} onMouseEnter={() => setHover({ x: cx, y: cy, content: killLabel(e) })} onMouseLeave={() => setHover(null)} />;
              })}
              {mapMode === 'you' && d.kills.filter((e) => e.victimId === d.meId && e.x != null).map((e, i) => {
                const { cx, cy } = toXY(e.x!, e.y!);
                return (
                  <g key={`d${i}`}>
                    <g stroke="var(--loss)" strokeWidth={2.4}>
                      <line x1={cx - 5} y1={cy - 5} x2={cx + 5} y2={cy + 5} />
                      <line x1={cx - 5} y1={cy + 5} x2={cx + 5} y2={cy - 5} />
                    </g>
                    <circle cx={cx} cy={cy} r={8} fill="transparent" style={{ cursor: 'pointer' }} onMouseEnter={() => setHover({ x: cx, y: cy, content: killLabel(e) })} onMouseLeave={() => setHover(null)} />
                  </g>
                );
              })}
              {selDeath && selDeath.x != null && selDeath.y != null && (() => {
                const { cx, cy } = toXY(selDeath.x, selDeath.y);
                return (
                  <g key="sel" style={{ pointerEvents: 'none' }}>
                    <circle cx={cx} cy={cy} r={11} fill="none" stroke="var(--loss)" strokeWidth={2.5}>
                      <animate attributeName="r" values="9;17;9" dur="1.5s" repeatCount="indefinite" />
                      <animate attributeName="opacity" values="1;0.25;1" dur="1.5s" repeatCount="indefinite" />
                    </circle>
                    <g stroke="#fff" strokeWidth={3}>
                      <line x1={cx - 6} y1={cy - 6} x2={cx + 6} y2={cy + 6} />
                      <line x1={cx - 6} y1={cy + 6} x2={cx + 6} y2={cy - 6} />
                    </g>
                  </g>
                );
              })()}
              {curEvt && curEvt.x != null && curEvt.y != null && (() => {
                const { cx, cy } = toXY(curEvt.x, curEvt.y);
                const col = CAT_COLOR[curEvt.cat];
                return (
                  <g key="curpin" style={{ pointerEvents: 'none' }}>
                    <circle cx={cx} cy={cy} r={11} fill="none" stroke={col} strokeWidth={2.5}>
                      <animate attributeName="r" values="9;18;9" dur="1.4s" repeatCount="indefinite" />
                      <animate attributeName="opacity" values="1;0.2;1" dur="1.4s" repeatCount="indefinite" />
                    </circle>
                    <circle cx={cx} cy={cy} r={3} fill={col} stroke="#0b0d12" strokeWidth={1} />
                  </g>
                );
              })()}
            </svg>
            {hover && (
              <div className="map-tip" style={{ left: `${(hover.x / MAP_SIZE) * 100}%`, top: `${(hover.y / MAP_SIZE) * 100}%` }}>
                {hover.content}
              </div>
            )}
            <div className="row wrap" style={{ gap: 12, marginTop: 8 }}>
              <span className="side-badge" style={{ borderColor: mySideColor, color: mySideColor }}>You → {mySide} side</span>
              {mapMode === 'you' ? (
                <>
                  <span className="note"><span style={{ color: 'var(--win)' }}>●</span> your kills ({d.k})</span>
                  <span className="note"><span style={{ color: 'var(--loss)' }}>✕</span> your deaths ({d.d})</span>
                </>
              ) : (
                <>
                  <span className="note"><span style={{ color: 'var(--teal)' }}>●</span> your team's kills</span>
                  <span className="note"><span style={{ color: 'var(--warn)' }}>●</span> enemy kills</span>
                </>
              )}
              <span className="note" style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}><ObjectiveIcon kind="Dragon" size={14} /> dragon <ObjectiveIcon kind="Herald" size={14} /> herald <ObjectiveIcon kind="Baron" size={14} /> baron</span>
            </div>
          </div>
            <aside className="event-list">
              <div className="event-list-head note">{timelineEvents.length} events · {mapMode === 'you' ? 'your kills & deaths' : 'all kills'} + objectives</div>
              <div className="event-list-scroll">
                {timelineEvents.length === 0 ? (
                  <div className="note" style={{ padding: '8px 4px' }}>No events to show.</div>
                ) : timelineEvents.map((ev, i) => (
                  <div
                    key={i}
                    id={`ev-row-${i}`}
                    className={`event-row ${ev.cat} ${i === curIdx ? 'selected' : ''}`}
                    onClick={() => selectEvent(i)}
                    onMouseEnter={() => { if (ev.x != null && ev.y != null) { const { cx, cy } = toXY(ev.x, ev.y); setHover({ x: cx, y: cy, content: eventTip(ev) }); } }}
                    onMouseLeave={() => setHover(null)}
                    role="button"
                    title="Show this event on the map"
                  >
                    <span className="ev-min tnum">{mmss(ev.ts)}</span>
                    {ev.obj
                      ? <ObjectiveIcon kind={ev.obj.kind} element={ev.obj.element} size={22} />
                      : <ChampionIcon championName={evActor(ev)} size={22} />}
                    <span className="ev-text">{evShort(ev)}</span>
                  </div>
                ))}
              </div>
            </aside>
          </div>
          {timelineEvents.length > 0 && (
            <div className="timeline-scrubber">
              <div className="tl-track" aria-hidden="true">
                {timelineEvents.map((ev, i) => (
                  <button
                    key={i}
                    type="button"
                    className={`tl-tick ${ev.cat} ${i === curIdx ? 'on' : ''}`}
                    style={{ left: `${(ev.ts / endMs) * 100}%`, background: CAT_COLOR[ev.cat] }}
                    title={mmss(ev.ts)}
                    onClick={() => selectEvent(i)}
                  />
                ))}
              </div>
              <input
                className="tl-range"
                type="range"
                min={0}
                max={timelineEvents.length - 1}
                step={1}
                value={curIdx ?? 0}
                onChange={(e) => selectEvent(Number(e.target.value))}
              />
              <div className="tl-desc">
                <button type="button" className="tl-nav" disabled={curIdx == null || curIdx <= 0} onClick={() => selectEvent((curIdx ?? 0) - 1)} title="Previous event">‹</button>
                <div className="tl-desc-body">
                  {curEvt ? evDesc(curEvt) : <span className="note">Drag the slider or pick an event to step through the game — {timelineEvents.length} in total.</span>}
                </div>
                <button type="button" className="tl-nav" disabled={curIdx != null && curIdx >= timelineEvents.length - 1} onClick={() => selectEvent((curIdx ?? -1) + 1)} title="Next event">›</button>
              </div>
            </div>
          )}
        </Panel>

        {/* Scorecard */}
        <Panel title="Performance" action={<span className="grade-pill" style={{ background: gradeColor(d.grade) }}>{d.grade}</span>}>
          <div className="tiles">
            <StatTile label="Kill participation" value={`${round(d.kp)}%`} sub={`${d.k}/${d.d}/${d.a} · KDA ${round(d.kda, 2)}`} />
            <StatTile label="Damage share" value={`${round(d.dmgShare)}%`} sub={`${round(d.dmgPerMin)}/min`} />
            <StatTile label="Gold share" value={`${round(d.goldShare)}%`} />
            <StatTile label="CS / min" value={round(d.csPerMin, 1)} sub={`${d.finalCs} total`} />
            <StatTile label="Vision" value={d.wardsPlaced + d.wardsKilled} sub={`${d.wardsPlaced} placed · ${d.wardsKilled} cleared · ${d.controlWards} pinks`} />
            <StatTile label="Solo kills" value={d.solo} sub={d.lvl6 != null ? `lvl 6 @ ${round(d.lvl6, 1)}m` : undefined} />
          </div>
        </Panel>
      </div>

      {/* Gold graph */}
      <Panel title="Gold advantage over time" sub="Your team's gold lead (teal) and your lane gold vs opponent (gold)">
        <ResponsiveContainer width="100%" height={210}>
          <LineChart data={d.goldSeries} margin={{ top: 6, right: 10, bottom: 0, left: 6 }}>
            <XAxis dataKey="min" tickLine={false} axisLine={{ stroke: 'var(--border)' }} tickFormatter={(m) => `${m}'`} interval="preserveStartEnd" />
            <YAxis tickLine={false} axisLine={false} width={46} tickFormatter={(v) => `${Math.round(v / 1000)}k`} />
            <ReferenceLine y={0} stroke="var(--border-2)" />
            <Tooltip
              contentStyle={{ background: 'var(--panel-3)', border: '1px solid var(--border-2)', borderRadius: 8 }}
              labelFormatter={(m) => `${m} min`}
              formatter={(v: number, name) => [`${v > 0 ? '+' : ''}${formatNumber(v)}g`, name === 'teamDiff' ? 'Team lead' : 'Lane']}
            />
            <Line type="monotone" dataKey="teamDiff" stroke="var(--teal)" strokeWidth={2.5} dot={false} />
            <Line type="monotone" dataKey="laneDiff" stroke="var(--gold)" strokeWidth={2} strokeDasharray="4 3" dot={false} connectNulls />
          </LineChart>
        </ResponsiveContainer>
      </Panel>

      {/* Laning report */}
      <Panel title="Laning phase" sub={d.opp ? `You vs ${d.opp.championName ?? 'lane opponent'} (${positionLabel(d.me.teamPosition)})` : 'No direct lane opponent detected'}>
        {!d.opp && <EmptyState emoji="🔀" message="Couldn't pin a lane opponent" hint="Fill or roaming games don't always have a clean 1-v-1 lane." />}
        {d.opp && (
          <div className="grid cols-2">
            {[{ label: '@ 10 min', l: d.lane10 }, { label: '@ 14 min', l: d.lane14 }].map((blk) => (
              <div key={blk.label} className="lane-block">
                <div className="section-title" style={{ margin: '0 0 8px' }}>{blk.label}</div>
                {!blk.l ? <span className="note">game ended earlier</span> : (
                  <div className="tiles">
                    <StatTile label="CS diff" value={sign(blk.l.cs)} tone={blk.l.cs >= 0 ? 'win' : 'loss'} sub={`${blk.l.meCs} vs ${blk.l.oppCs}`} />
                    <StatTile label="Gold diff" value={sign(blk.l.gold)} tone={blk.l.gold >= 0 ? 'win' : 'loss'} />
                    <StatTile label="XP diff" value={sign(blk.l.xp)} tone={blk.l.xp >= 0 ? 'win' : 'loss'} />
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </Panel>

      {/* Item & back timeline */}
      <Panel title="Item & back timeline" sub={d.opp ? `Your shopping vs ${d.opp.championName ?? 'opponent'} · 💀 dead back · ↩ recall · spent / held / total gold per shop` : 'Your shopping visits'}>
        <div className="grid cols-2">
          {[{ who: 'You', backs: d.myBacks, endGold: d.myEndGold, isMe: true }, ...(d.opp ? [{ who: d.opp.championName ?? 'Opponent', backs: d.oppBacks, endGold: d.oppEndGold, isMe: false }] : [])].map((col) => (
            <div key={col.who} className="lane-block">
              <div className="spread" style={{ marginBottom: 8 }}>
                <span className="section-title" style={{ margin: 0 }}>{col.who}</span>
                <span className="note tnum">{col.backs.length} shops · <b style={{ color: 'var(--gold)' }}>{formatNumber(col.endGold)}g</b> earned</span>
              </div>
              {col.backs.length === 0 ? (
                <span className="note">no purchases recorded</span>
              ) : (
                <div className="stack" style={{ gap: 8 }}>
                  {col.backs.map((b, i) => {
                    const kind = i === 0 ? 'start' : b.dead ? 'dead' : 'recall';
                    const kindTitle = kind === 'dead' ? 'Dead back — shopped after dying' : kind === 'recall' ? 'Recall / walked back (alive)' : 'Starting items';
                    const spent = b.items.reduce((sum, id) => sum + (items[id]?.base ?? items[id]?.gold ?? 0), 0);
                    const deathIdx = col.isMe && b.deathTs != null ? d.deaths.findIndex((x) => x.ts === b.deathTs) : -1;
                    return (
                      <div key={i} className={`back-entry ${kind}`}>
                        <div className="back-head">
                          <span className="back-time tnum">{mmss(b.t)}</span>
                          {kind === 'dead' && b.killer ? (
                            deathIdx >= 0 ? (
                              <button type="button" className="deadback link" onClick={() => jumpToMapDeath(deathIdx)} title={`Killed by ${b.killer} — jump to this death`}>
                                💀 <ChampionIcon championName={b.killer} size={16} /> <span className="db-name">{b.killer}</span> <span className="arrow">↗</span>
                              </button>
                            ) : (
                              <span className="deadback" title={`Killed by ${b.killer}`}>💀 <ChampionIcon championName={b.killer} size={16} /> <span className="db-name">{b.killer}</span></span>
                            )
                          ) : (
                            <span className={`back-kind ${kind}`} title={kindTitle}>{kind === 'recall' ? '↩' : '•'}</span>
                          )}
                          <div className="back-items">
                            {b.items.map((id, j) => {
                              const url = itemIconUrl(id, version);
                              return url ? (
                                <img key={j} className="item-slot-img" src={url} alt="" title={items[id]?.name ?? `Item ${id}`} loading="lazy" />
                              ) : (
                                <span key={j} className="item-slot" />
                              );
                            })}
                          </div>
                        </div>
                        <div className="back-golds tnum">
                          <span title="Gold spent on these items this visit">spent <b>{formatNumber(spent)}g</b></span>
                          <span className="sep">·</span>
                          <span title="Gold in your pocket at this moment">held <b>{formatNumber(b.heldGold)}g</b></span>
                          <span className="sep">·</span>
                          <span title="Total gold earned so far">total <b style={{ color: 'var(--gold)' }}>{formatNumber(b.totalGold)}g</b></span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          ))}
        </div>
      </Panel>

      {/* Back timing */}
      <Panel title="Back timing" sub="Gold sitting in your pocket is gold not on the map — recall to spend it at the right moment">
        <div className="tiles">
          <StatTile label="Recalls (shop visits)" value={d.myBacks.length} sub={d.opp ? `opponent ${d.oppBacks.length}` : undefined} />
          <StatTile label="Most gold you held" value={formatNumber(d.myPeakGold)} sub={`at ${d.myPeakMin}'`} tone={d.myPeakGold - d.oppPeakGold > 400 && d.myPeakGold >= 1300 ? 'loss' : undefined} />
          {d.opp && <StatTile label="Opponent's peak held" value={formatNumber(d.oppPeakGold)} />}
        </div>
        <p className="note" style={{ marginTop: 10 }}>
          {d.myPeakGold - d.oppPeakGold > 400 && d.myPeakGold >= 1300
            ? `You sat on ${formatNumber(d.myPeakGold)}g around ${d.myPeakMin}' — ${formatNumber(d.myPeakGold - d.oppPeakGold)}g more than your opponent ever held. Recalling sooner would have put that power spike on the board earlier.`
            : d.opp && d.myPeakGold <= d.oppPeakGold + 100
              ? `Good tempo — you spent gold at least as promptly as your opponent (you peaked at ${formatNumber(d.myPeakGold)}g, they peaked at ${formatNumber(d.oppPeakGold)}g).`
              : `Your biggest stockpile was ${formatNumber(d.myPeakGold)}g${d.opp ? ` vs the opponent's ${formatNumber(d.oppPeakGold)}g` : ''}. Watch for moments where holding gold delays a key item.`}
        </p>
      </Panel>

      <div className="dash">
        {/* Death review */}
        <Panel title="Death review" sub={`${d.deaths.length} deaths · click one to pin it on the map`}>
          {d.deaths.length === 0 ? (
            <EmptyState emoji="🛡️" message="Deathless game — well played." />
          ) : (
            <div className="stack" style={{ gap: 8 }}>
              {d.deaths.map((x, i) => (
                <div key={i} className={`death-row clickable ${selectedDeath === i ? 'selected' : ''}`} onClick={() => jumpToMapDeath(i)} title="Show this death on the map" role="button">
                  <span className="death-min tnum">{round(x.min, 1)}'</span>
                  <ChampionIcon championName={x.killer} size={26} />
                  <span>killed by <b>{x.killer}</b></span>
                  {x.alone && <span className="badge" style={{ color: 'var(--loss)' }}>caught alone</span>}
                  {x.shutdown > 0 && <span className="note">gave {x.shutdown}g shutdown</span>}
                  <span className="right map-hint">📍 map</span>
                </div>
              ))}
            </div>
          )}
        </Panel>

        {/* Objectives */}
        <Panel title="Objective participation" sub="Elite monsters your team took, and whether you were there">
          {objSummary.length === 0 ? (
            <EmptyState emoji="🐉" message="Your team took no elite objectives" />
          ) : (
            <div className="stack" style={{ gap: 10 }}>
              <div className="row wrap" style={{ gap: 8 }}>
                {objSummary.map((o) => (
                  <span key={o.kind} className={`chip ${o.present === o.total ? 'active' : ''}`} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <ObjectiveIcon kind={o.kind as ObjKind} size={15} /> {o.kind}: {o.present}/{o.total} present
                  </span>
                ))}
              </div>
              {dragCount.length > 0 && (
                <div className="note">You were present for {dragCount.filter((o) => o.present).length} of {dragCount.length} of your team's dragons.</div>
              )}
            </div>
          )}
        </Panel>
      </div>
    </div>
  );
}
