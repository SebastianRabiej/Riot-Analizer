import { useEffect, useMemo, useRef, useState } from 'react';
import { Panel, Segmented } from '../ui';
import { useDdragonVersion } from '../../hooks';
import {
  BOOTS, CELL, MAP_MAX, MAP_MIN, MAP_SPAN, N, Region, ROLE_ROUTE, SPOTS, TURRET_POINTS, cellCenter, cellOf, idx, minimapGrid,
  moveSpeeds, pathLength, pathTo, sampleMinimap, schematicGrid, snapToGrid, spotById, travelField,
  type MinimapSample, type Pt,
} from '../../lib/decision/pathing';
import type { Role } from '../../lib/decision';
import { Select, Slider, Toggle, fmt } from './controls';

const BANDS: [number, [number, number, number]][] = [[5, [51, 224, 192]], [10, [70, 209, 127]], [15, [75, 155, 255]], [20, [160, 114, 255]], [30, [233, 182, 76]], [45, [240, 87, 107]]];
const REGION_RGB: Record<number, [number, number, number]> = {
  [Region.LANE]: [52, 57, 74], [Region.RIVER]: [24, 62, 86], [Region.JUNGLE]: [28, 46, 40], [Region.BASE]: [36, 42, 58], [Region.PIT]: [48, 40, 72],
};
const CUSTOM = 'custom';
const units = (n: number) => Math.round(n).toLocaleString('en-US');

/** Paints one colour per grid cell onto `target`, scaled to the map projection. */
function blitCells(target: CanvasRenderingContext2D, colorOf: (c: number) => [number, number, number] | null, alpha: number) {
  const small = document.createElement('canvas');
  small.width = N; small.height = N;
  const sc = small.getContext('2d')!, img = sc.createImageData(N, N), d = img.data;
  for (let c = 0; c < N * N; c++) {
    const col = colorOf(c);
    if (!col) continue;
    const i = c % N, j = Math.floor(c / N), o = ((N - 1 - j) * N + i) * 4;
    d[o] = col[0]; d[o + 1] = col[1]; d[o + 2] = col[2]; d[o + 3] = 255;
  }
  sc.putImageData(img, 0, 0);
  const s = target.canvas.width / MAP_SPAN;
  target.save(); target.globalAlpha = alpha; target.imageSmoothingEnabled = true;
  target.drawImage(small, (0 - MAP_MIN) * s, (MAP_MAX - N * CELL) * s, N * CELL * s, N * CELL * s);
  target.restore();
}

export function TravelMap({ role, onUseSeconds }: { role: Role; onUseSeconds: (sec: number) => void }) {
  const version = useDdragonVersion();
  const [ms, setMs] = useState({ base: 340, boots: 45, flat: 0, percent: 0, jungleQuest: false });
  const [route, setRoute] = useState<[string, string]>(ROLE_ROUTE[role]);
  const [custom, setCustom] = useState<{ a: Pt | null; b: Pt | null }>({ a: null, b: null });
  const [clickSets, setClickSets] = useState<'a' | 'b'>('a');
  const [fight, setFight] = useState(10);
  const [source, setSource] = useState<'minimap' | 'schematic'>('minimap');
  const [threshold, setThreshold] = useState<number | null>(null);
  const [showWalls, setShowWalls] = useState(false);
  const [minimap, setMinimap] = useState<{ img: HTMLImageElement; sample: MinimapSample | null } | null>(null);
  const [status, setStatus] = useState('Loading the Riot minimap…');
  const [hover, setHover] = useState<number>(-1);
  const [width, setWidth] = useState(600);
  const wrapRef = useRef<HTMLDivElement>(null), canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => { setRoute(ROLE_ROUTE[role]); }, [role]);

  // Load map11.png (same image and projection as the match map) and read its pixels for wall detection.
  useEffect(() => {
    let active = true;
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      if (!active) return;
      try {
        const c = document.createElement('canvas');
        c.width = img.naturalWidth; c.height = img.naturalHeight;
        const x = c.getContext('2d', { willReadFrequently: true })!;
        x.drawImage(img, 0, 0);
        const sample = sampleMinimap(x.getImageData(0, 0, c.width, c.height).data, c.width, c.height);
        setMinimap({ img, sample });
        setThreshold(sample.autoThreshold);
        setStatus(`Walls auto-detected from the Riot minimap (${version}). Turn on “Show detected walls” to check them.`);
      } catch {
        setMinimap({ img, sample: null });
        setSource('schematic');
        setStatus('The browser blocked reading the minimap’s pixels — walls come from the hand-drawn schematic.');
      }
    };
    img.onerror = () => { if (active) { setSource('schematic'); setStatus('Could not load the Riot minimap — using the hand-drawn schematic.'); } };
    img.src = `https://ddragon.leagueoflegends.com/cdn/${version}/img/map/map11.png`;
    return () => { active = false; };
  }, [version]);

  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setWidth(el.clientWidth || 600));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const useMinimap = source === 'minimap' && !!minimap?.sample && threshold !== null;
  const grid = useMemo(() => (useMinimap ? minimapGrid(minimap!.sample!, threshold!) : schematicGrid()), [useMinimap, minimap, threshold]);
  const speed = moveSpeeds(ms);
  const pointOf = (id: string, which: 'a' | 'b'): Pt => (id === CUSTOM && custom[which]) ? custom[which]! : (spotById(id) ?? spotById(which === 'a' ? 'midc' : 'botc')!).p;
  const startCell = snapToGrid(grid, pointOf(route[0], 'a'));
  const destCell = snapToGrid(grid, pointOf(route[1], 'b'));
  const field = useMemo(() => travelField(grid, startCell, speed), [grid, startCell, speed.lane, speed.jungle]);

  const name = (id: string, which: 'a' | 'b') => (id === CUSTOM ? `your point ${which.toUpperCase()}` : spotById(id)?.name ?? id);
  const t = field.dist[destCell];
  const path = useMemo(() => pathTo(field, destCell), [field, destCell]);
  const pathLen = pathLength(path);
  const [ax, ay] = cellCenter(startCell), [bx, by] = cellCenter(destCell);
  const straight = Math.hypot(ax - bx, ay - by);
  const roamSec = Math.min(120, Math.max(15, Math.round((2 * t + fight) / 5) * 5));
  const rows = useMemo(() => SPOTS.filter((s) => s.id !== route[0]).map((s) => {
    const c = snapToGrid(grid, s.p), [cx, cy] = cellCenter(c), st = Math.hypot(cx - ax, cy - ay);
    return { s, t: field.dist[c], detour: st > 0 ? pathLength(pathTo(field, c)) / st : 1 };
  }).filter((r) => Number.isFinite(r.t)).sort((p, q) => p.t - q.t), [field, grid, route, ax, ay]);

  // ---- drawing
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    canvas.width = Math.round(width * dpr); canvas.height = Math.round(width * dpr);
    const ctx = canvas.getContext('2d')!, s = canvas.width / MAP_SPAN;
    const toPx = ([x, y]: Pt): Pt => [(x - MAP_MIN) * s, (MAP_MAX - y) * s];
    ctx.fillStyle = '#0a140a'; ctx.fillRect(0, 0, canvas.width, canvas.height);
    if (minimap) ctx.drawImage(minimap.img, 0, 0, MAP_SPAN * s, MAP_SPAN * s);
    if (!useMinimap) blitCells(ctx, (c) => REGION_RGB[grid[c]] ?? null, minimap ? 0.5 : 1);
    if (showWalls) blitCells(ctx, (c) => (!grid[c] && (!minimap?.sample || minimap.sample.inMap[c]) ? [230, 70, 70] : null), 0.45);
    blitCells(ctx, (c) => { const v = field.dist[c]; if (!Number.isFinite(v)) return null; for (const [lim, col] of BANDS) if (v <= lim) return col; return null; }, 0.38);
    if (!minimap) {
      TURRET_POINTS.forEach(([x, y, side]) => { const [px, py] = toPx([x, y]); ctx.fillStyle = side === 'b' ? '#4b9bff' : '#f0576b'; ctx.fillRect(px - 4, py - 4, 8, 8); });
    }
    const a = toPx(cellCenter(startCell)), b = toPx(cellCenter(destCell));
    ctx.setLineDash([3, 5]); ctx.strokeStyle = '#aab2c5'; ctx.lineWidth = 1.5; ctx.beginPath(); ctx.moveTo(a[0], a[1]); ctx.lineTo(b[0], b[1]); ctx.stroke(); ctx.setLineDash([]);
    if (Number.isFinite(field.dist[destCell])) {
      ctx.beginPath(); path.forEach((c, k) => { const p = toPx(cellCenter(c)); if (k) ctx.lineTo(p[0], p[1]); else ctx.moveTo(p[0], p[1]); });
      ctx.lineJoin = 'round'; ctx.strokeStyle = '#0b0d12'; ctx.lineWidth = 6; ctx.stroke(); ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 3; ctx.stroke();
    }
    const ring = (p: Pt, col: string, label: string) => {
      ctx.beginPath(); ctx.arc(p[0], p[1], 9 * dpr, 0, 7); ctx.fillStyle = col; ctx.fill(); ctx.lineWidth = 3; ctx.strokeStyle = '#0b0d12'; ctx.stroke();
      ctx.fillStyle = '#0b0d12'; ctx.font = `bold ${11 * dpr}px Inter, system-ui, sans-serif`; ctx.textAlign = 'center'; ctx.fillText(label, p[0], p[1] + 4 * dpr);
    };
    ring(a, '#33e0c0', 'A'); ring(b, '#e9b64c', 'B');
    if (hover >= 0 && grid[hover]) { const h = toPx(cellCenter(hover)); ctx.beginPath(); ctx.arc(h[0], h[1], 5 * dpr, 0, 7); ctx.strokeStyle = '#fff'; ctx.lineWidth = 2; ctx.stroke(); }
  }, [width, minimap, useMinimap, grid, showWalls, field, path, startCell, destCell, hover]);

  const toGame = (e: React.MouseEvent<HTMLCanvasElement>): Pt => {
    const r = e.currentTarget.getBoundingClientRect();
    return [MAP_MIN + ((e.clientX - r.left) / r.width) * MAP_SPAN, MAP_MAX - ((e.clientY - r.top) / r.height) * MAP_SPAN];
  };
  const spotOptions = [...SPOTS.map((s) => ({ value: s.id, label: s.name })), { value: CUSTOM, label: 'Custom point (click the map)' }];
  const hoverText = hover < 0 ? 'Hover the map to see the travel time to any point.' : !grid[hover] ? 'Cursor: wall / not walkable' : Number.isFinite(field.dist[hover]) ? `Cursor: ${fmt(field.dist[hover], 1)}s from A` : 'Cursor: unreachable';

  return (
    <Panel title="Travel map" sub="What “10s” and “20s” of walking mean — paths go around walls, not in a straight line">
      <div className="dm-travel">
        <div className="stack" style={{ gap: 12 }}>
          <div className="dm-grid-2">
            <Select label="Champion base MS" value={ms.base} options={[325, 330, 335, 340, 345, 350, 355].map((v) => ({ value: v, label: String(v) }))} onChange={(base) => setMs({ ...ms, base })} />
            <Select label="Boots" value={ms.boots} options={BOOTS} onChange={(boots) => setMs({ ...ms, boots })} />
          </div>
          <div className="dm-grid-2">
            <Slider label="Bonus % MS" value={ms.percent} min={0} max={60} format={(v) => `${v}%`} onChange={(percent) => setMs({ ...ms, percent })} hint="Ghost, Phase Rush, item passives…" />
            <label className="dm-field"><span className="dm-label">Extra flat MS</span>
              <input className="dm-input" type="number" min={0} max={200} step={5} value={ms.flat} onChange={(e) => setMs({ ...ms, flat: Math.max(0, +e.target.value || 0) })} />
            </label>
          </div>
          <Toggle checked={ms.jungleQuest} onChange={(jungleQuest) => setMs({ ...ms, jungleQuest })} title="Jungle quest: +8% out of combat in jungle & river" />
          <div className="tile dm-ms">
            <div><div className="tlabel">Final movement speed</div><div className="tvalue dm-teal">{units(speed.lane)}</div>
              <div className="tsub">{speed.raw > 415 ? `soft-capped from ${units(speed.raw)} · ` : ''}{speed.jungle !== speed.lane ? `${units(speed.jungle)} in jungle/river` : 'units per second'}</div></div>
            <div className="dm-per">{[5, 10, 20, 30].map((sec) => <div key={sec}><b>{sec}s</b>{units(speed.lane * sec)}</div>)}</div>
          </div>
          <div className="dm-grid-2">
            <Select label="A · start" value={route[0]} options={spotOptions} onChange={(v) => setRoute([v, route[1]])} />
            <Select label="B · destination" value={route[1]} options={spotOptions} onChange={(v) => setRoute([route[0], v])} />
          </div>
          <div><Segmented<'a' | 'b'> options={[{ value: 'a', label: 'Click sets A' }, { value: 'b', label: 'Click sets B' }]} value={clickSets} onChange={setClickSets} /></div>
          {Number.isFinite(t) ? (
            <div className="tile">
              <div className="tlabel">{name(route[0], 'a')} → {name(route[1], 'b')}</div>
              <div className="tvalue dm-gold">{fmt(t, 1)}s</div>
              <div className="tsub">Path {units(pathLen)} units vs {units(straight)} straight: <b>{fmt(straight > 0 ? pathLen / straight : 1, 2)}×</b> longer. Roam ≈ {fmt(t, 1)}s + {fight}s fight + {fmt(t, 1)}s back.</div>
            </div>
          ) : <div className="tile"><div className="tsub">No walkable path found.</div></div>}
          <Slider label="Time spent fighting" value={fight} min={0} max={30} format={(v) => `${v}s`} onChange={setFight} />
          <button type="button" className="btn btn-teal" disabled={!Number.isFinite(t)} onClick={() => onUseSeconds(roamSec)}>Use {roamSec}s in the roam scenario</button>
        </div>
        <div className="stack" style={{ gap: 10 }}>
          <div ref={wrapRef} className="dm-map">
            <canvas
              ref={canvasRef}
              style={{ width: '100%', height: '100%' }}
              aria-label="Summoner's Rift travel-time map. Click to set points."
              onClick={(e) => { const p = toGame(e); setCustom((c) => ({ ...c, [clickSets]: p })); setRoute((r) => (clickSets === 'a' ? [CUSTOM, r[1]] : [r[0], CUSTOM])); }}
              onMouseMove={(e) => { const [i, j] = cellOf(toGame(e)); setHover(idx(i, j)); }}
              onMouseLeave={() => setHover(-1)}
            />
          </div>
          <div className="note">{hoverText}</div>
          <div className="streak-chips">
            {BANDS.map(([lim, [r, g, b]]) => <span key={lim} className="streak-chip"><i className="dm-sw" style={{ background: `rgb(${r},${g},${b})` }} />≤{lim}s</span>)}
            <span className="streak-chip"><i className="dm-sw" style={{ background: '#fff' }} />Path</span>
            <span className="streak-chip"><i className="dm-sw" style={{ background: '#aab2c5' }} />Straight line</span>
          </div>
          <div className="dm-grid-2">
            <Select label="Walls come from" value={source} onChange={setSource}
              options={[{ value: 'minimap', label: 'Riot minimap · auto-detected' }, { value: 'schematic', label: 'Hand-drawn schematic' }]} />
            <Slider label="Wall detection" value={threshold ?? 80} min={10} max={200} onChange={setThreshold}
              format={(v) => (useMinimap ? `${v}${v === minimap?.sample?.autoThreshold ? ' (auto)' : ''}` : '—')} hint="Higher = more dark terrain counts as wall." />
          </div>
          <Toggle checked={showWalls} onChange={setShowWalls} title="Show detected walls" sub="Red tint = not walkable." />
          <div className="note">{status}</div>
        </div>
      </div>
      <div className="table-scroll" style={{ marginTop: 14, maxHeight: 320 }}>
        <table className="tbl dm-tbl-compact">
          <thead><tr><th>From {name(route[0], 'a')}</th><th className="num">Time</th><th className="num">Detour</th></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.s.id} className={`clickable ${r.s.id === route[1] ? 'dm-sel' : ''}`} onClick={() => setRoute([route[0], r.s.id])}>
                <td><i className={`dm-dot ${r.s.side}`} />{r.s.name}</td><td className="num">{fmt(r.t, 1)}s</td><td className="num">{fmt(r.detour, 2)}×</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Panel>
  );
}
