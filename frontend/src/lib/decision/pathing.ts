// Travel-time pathing on Summoner's Rift: walkable grid, movement speed and Dijkstra.
// Walls come from Riot's minimap (Data Dragon map11.png, same projection as MatchAnalysis.tsx)
// when its pixels can be read; otherwise from a hand-built schematic of lanes, river and jungle paths.
import type { Role } from './constants';

export type Pt = [number, number];
export const SR_SIZE = 14870;
export const CELL = 75;
export const N = Math.ceil(SR_SIZE / CELL);
/** map11.png spans game coordinates −120…14870 on both axes (see MatchAnalysis.tsx MAP_CONFIGS). */
export const MAP_MIN = -120, MAP_MAX = 14870, MAP_SPAN = MAP_MAX - MAP_MIN;

export const Region = { WALL: 0, LANE: 1, RIVER: 2, JUNGLE: 3, BASE: 4, PIT: 5 } as const;
export type RegionId = (typeof Region)[keyof typeof Region];

const mirror = ([x, y]: Pt): Pt => [14840 - x, 14920 - y]; // blue → red (the map is ~point-symmetric)

// ---------------------------------------------------------------- schematic shapes
type Shape =
  | { t: 'seg'; pts: Pt[]; w: number; region: RegionId }
  | { t: 'circ'; c: Pt; r: number; region: RegionId }
  | { t: 'poly'; pts: Pt[]; region: RegionId };

const SHAPES: Shape[] = [];
const BASE_POLY: Pt[] = [[150, 150], [4100, 150], [4300, 1900], [3900, 3100], [3100, 3900], [1900, 4300], [150, 4100]];
SHAPES.push({ t: 'poly', pts: BASE_POLY, region: Region.BASE }, { t: 'poly', pts: BASE_POLY.map(mirror), region: Region.BASE });
SHAPES.push(
  { t: 'seg', pts: [[1100, 3900], [1100, 12700], [1900, 13700], [11000, 13700]], w: 1300, region: Region.LANE },
  { t: 'seg', pts: [[13740, 10950], [13740, 2220], [12940, 1220], [3900, 1220]], w: 1300, region: Region.LANE },
  { t: 'seg', pts: [[3500, 3500], [11340, 11420]], w: 1150, region: Region.LANE },
  { t: 'seg', pts: [[1900, 12700], [3200, 10700], [4400, 9600], [7420, 7460], [10440, 5320], [11640, 4220], [12940, 2220]], w: 1200, region: Region.RIVER },
  { t: 'circ', c: [5007, 10471], r: 700, region: Region.PIT },
  { t: 'circ', c: [9866, 4414], r: 700, region: Region.PIT },
);
const JUNGLE_PATHS: Pt[][] = [
  [[2400, 4200], [3200, 5200], [3800, 6500]], [[1700, 6600], [2800, 6500], [3800, 6500]], [[3800, 6500], [4700, 6000], [5350, 5650]],
  [[3800, 6500], [3400, 7200], [3800, 7900]], [[3800, 7900], [2900, 8000], [2100, 8400]], [[2100, 8400], [1500, 9100]],
  [[3800, 7900], [4300, 8700], [4600, 9400]], [[1600, 11100], [2700, 10600], [3300, 10500]], [[3800, 7900], [5000, 8000], [6200, 7600], [6700, 7300]],
  [[4300, 2300], [5500, 3000], [6600, 3500], [7800, 4000]], [[6300, 1900], [6200, 2900], [6600, 3500]], [[7800, 4000], [8300, 3300], [8400, 2700]],
  [[8400, 2700], [8700, 1900], [8800, 1500]], [[7800, 4000], [7300, 4800], [6950, 5400]], [[6950, 5400], [6300, 5900], [6150, 6250]],
  [[6950, 5400], [7600, 6200], [8100, 6800]], [[7800, 4000], [9000, 4600], [9500, 5300]], [[11000, 1700], [11500, 2600], [12000, 3000]],
];
JUNGLE_PATHS.forEach((p) => SHAPES.push({ t: 'seg', pts: p, w: 600, region: Region.JUNGLE }, { t: 'seg', pts: p.map(mirror), w: 600, region: Region.JUNGLE }));
const CAMPS_BLUE: [string, number, number][] = [['Blue buff', 3800, 7900], ['Gromp', 2100, 8400], ['Wolves', 3800, 6500], ['Red buff', 7800, 4000], ['Raptors', 6950, 5400], ['Krugs', 8400, 2700]];
CAMPS_BLUE.forEach(([, x, y]) => SHAPES.push({ t: 'circ', c: [x, y], r: 420, region: Region.JUNGLE }, { t: 'circ', c: mirror([x, y]), r: 420, region: Region.JUNGLE }));

// ---------------------------------------------------------------- key spots
export interface Spot { id: string; name: string; p: Pt; side: 'b' | 'r' | 'n'; kind?: 'turret' | 'camp' | 'pit' }
export const SPOTS: Spot[] = [
  { id: 'bf', name: 'Blue fountain', p: [500, 520], side: 'b' },
  { id: 'btop', name: 'Blue top outer turret', p: [981, 10441], side: 'b', kind: 'turret' },
  { id: 'bmid', name: 'Blue mid outer turret', p: [5846, 6396], side: 'b', kind: 'turret' },
  { id: 'bbot', name: 'Blue bot outer turret', p: [10504, 1029], side: 'b', kind: 'turret' },
  { id: 'rtop', name: 'Red top outer turret', p: [4318, 13875], side: 'r', kind: 'turret' },
  { id: 'rmid', name: 'Red mid outer turret', p: [8955, 8510], side: 'r', kind: 'turret' },
  { id: 'rbot', name: 'Red bot outer turret', p: [13866, 4505], side: 'r', kind: 'turret' },
  { id: 'topc', name: 'Top lane (corner)', p: [1500, 13300], side: 'n' },
  { id: 'midc', name: 'Mid lane (center)', p: [7420, 7460], side: 'n' },
  { id: 'botc', name: 'Bot lane (corner)', p: [13340, 1600], side: 'n' },
  { id: 'drag', name: 'Dragon pit', p: [9866, 4414], side: 'n', kind: 'pit' },
  { id: 'baron', name: 'Baron pit', p: [5007, 10471], side: 'n', kind: 'pit' },
  { id: 'scT', name: 'Top scuttle', p: [4400, 9600], side: 'n' },
  { id: 'scB', name: 'Bot scuttle', p: [10440, 5320], side: 'n' },
  ...CAMPS_BLUE.map(([n, x, y]): Spot => ({ id: `b-${n}`, name: `Blue ${n.toLowerCase()}`, p: [x, y], side: 'b', kind: 'camp' })),
  ...CAMPS_BLUE.map(([n, x, y]): Spot => ({ id: `r-${n}`, name: `Red ${n.toLowerCase()}`, p: mirror([x, y]), side: 'r', kind: 'camp' })),
  { id: 'rf', name: 'Red fountain', p: mirror([500, 520]), side: 'r' },
];
export const spotById = (id: string) => SPOTS.find((s) => s.id === id);
/** Typical roam route per role: [start, destination]. */
export const ROLE_ROUTE: Record<Role, [string, string]> = {
  top: ['topc', 'midc'], jungle: ['b-Blue buff', 'topc'], mid: ['midc', 'botc'], bot: ['botc', 'midc'], support: ['botc', 'midc'],
};
/** All 22 turrets (Riot match-timeline coordinates) for drawing the schematic fallback. */
export const TURRET_POINTS: [number, number, 'b' | 'r'][] = [
  [981, 10441, 'b'], [1512, 6699, 'b'], [1169, 4287, 'b'], [5846, 6396, 'b'], [5048, 4812, 'b'], [3651, 3696, 'b'], [10504, 1029, 'b'], [6919, 1483, 'b'], [4281, 1253, 'b'], [1748, 2270, 'b'], [2177, 1807, 'b'],
  [4318, 13875, 'r'], [7943, 13411, 'r'], [10481, 13650, 'r'], [8955, 8510, 'r'], [9767, 10113, 'r'], [11134, 11207, 'r'], [13866, 4505, 'r'], [13327, 8226, 'r'], [13624, 10572, 'r'], [12611, 13084, 'r'], [13052, 12612, 'r'],
];

// ---------------------------------------------------------------- rasterising
function distToSegment(px: number, py: number, a: Pt, b: Pt) {
  const dx = b[0] - a[0], dy = b[1] - a[1], l = dx * dx + dy * dy;
  const t = l ? Math.max(0, Math.min(1, ((px - a[0]) * dx + (py - a[1]) * dy) / l)) : 0;
  return Math.hypot(a[0] + t * dx - px, a[1] + t * dy - py);
}
function inPolygon(px: number, py: number, pts: Pt[]) {
  let c = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const [xi, yi] = pts[i], [xj, yj] = pts[j];
    if ((yi > py) !== (yj > py) && px < ((xj - xi) * (py - yi)) / (yj - yi) + xi) c = !c;
  }
  return c;
}
const PRIORITY: Record<number, number> = { [Region.BASE]: 5, [Region.PIT]: 4, [Region.RIVER]: 3, [Region.LANE]: 2, [Region.JUNGLE]: 1 };
function raster(shapes: Shape[], scale = 1): Uint8Array {
  const g = new Uint8Array(N * N);
  for (let j = 0; j < N; j++) for (let i = 0; i < N; i++) {
    const x = (i + 0.5) * CELL, y = (j + 0.5) * CELL;
    let best = 0;
    for (const s of shapes) {
      let hit = false;
      if (s.t === 'seg') { for (let k = 0; k < s.pts.length - 1 && !hit; k++) hit = distToSegment(x, y, s.pts[k], s.pts[k + 1]) <= (s.w * scale) / 2; }
      else if (s.t === 'circ') hit = Math.hypot(x - s.c[0], y - s.c[1]) <= s.r * scale;
      else hit = inPolygon(x, y, s.pts);
      if (hit && (!best || PRIORITY[s.region] > PRIORITY[best])) best = s.region;
    }
    g[j * N + i] = best;
  }
  return g;
}
let schematicCache: Uint8Array | null = null, coreCache: Uint8Array | null = null;
/** Hand-built walkable grid (lanes, river, pits, bases, main jungle paths). */
export const schematicGrid = () => (schematicCache ??= raster(SHAPES));
/** Narrow lane/river/base cores that always stay walkable when walls come from the minimap. */
const coreGrid = () => (coreCache ??= raster(SHAPES.filter((s) => s.region !== Region.JUNGLE), 0.6));

export const idx = (i: number, j: number) => j * N + i;
export const cellOf = ([x, y]: Pt): [number, number] => [Math.min(N - 1, Math.max(0, Math.floor(x / CELL))), Math.min(N - 1, Math.max(0, Math.floor(y / CELL)))];
export const cellCenter = (c: number): Pt => [((c % N) + 0.5) * CELL, (Math.floor(c / N) + 0.5) * CELL];

// ---------------------------------------------------------------- walls from the minimap
export interface MinimapSample { luminance: Float32Array; water: Uint8Array; inMap: Uint8Array; autoThreshold: number }
/** Averages minimap pixels per grid cell. `rgba` is ImageData.data of the whole map11.png. */
export function sampleMinimap(rgba: Uint8ClampedArray, width: number, height: number): MinimapSample {
  const luminance = new Float32Array(N * N), water = new Uint8Array(N * N), inMap = new Uint8Array(N * N);
  const hist = new Float64Array(256);
  for (let j = 0; j < N; j++) for (let i = 0; i < N; i++) {
    const gx0 = i * CELL, gx1 = gx0 + CELL, gy0 = j * CELL, gy1 = gy0 + CELL;
    const px0 = Math.floor(((gx0 - MAP_MIN) / MAP_SPAN) * width), px1 = Math.max(px0 + 1, Math.ceil(((gx1 - MAP_MIN) / MAP_SPAN) * width));
    const py0 = Math.floor(((MAP_MAX - gy1) / MAP_SPAN) * height), py1 = Math.max(py0 + 1, Math.ceil(((MAP_MAX - gy0) / MAP_SPAN) * height));
    let r = 0, g = 0, b = 0, n = 0;
    for (let py = Math.max(0, py0); py < Math.min(height, py1); py++) for (let px = Math.max(0, px0); px < Math.min(width, px1); px++) {
      const o = (py * width + px) * 4; r += rgba[o]; g += rgba[o + 1]; b += rgba[o + 2]; n++;
    }
    const c = j * N + i;
    if (!n) continue;
    r /= n; g /= n; b /= n;
    const L = 0.299 * r + 0.587 * g + 0.114 * b;
    luminance[c] = L;
    water[c] = b > r + 18 && (b + g) / 2 > r + 14 && L > 30 ? 1 : 0;
    inMap[c] = gx1 <= SR_SIZE && gy1 <= SR_SIZE ? 1 : 0;
    if (inMap[c]) hist[Math.min(255, Math.round(L))]++;
  }
  return { luminance, water, inMap, autoThreshold: otsu(hist) };
}
function otsu(hist: Float64Array) {
  let total = 0, sum = 0;
  for (let k = 0; k < 256; k++) { total += hist[k]; sum += k * hist[k]; }
  let wB = 0, sB = 0, best = 0, thr = 80;
  for (let k = 0; k < 256; k++) {
    wB += hist[k]; if (!wB) continue;
    const wF = total - wB; if (!wF) break;
    sB += k * hist[k];
    const mB = sB / wB, mF = (sum - sB) / wF, between = wB * wF * (mB - mF) ** 2;
    if (between > best) { best = between; thr = k; }
  }
  return thr;
}
/** Walkable grid from minimap brightness: lanes/river/bases always kept, specks cleaned, only terrain connected to mid lane. */
export function minimapGrid(sample: MinimapSample, threshold: number): Uint8Array {
  const schem = schematicGrid(), core = coreGrid();
  let g = new Uint8Array(N * N);
  for (let c = 0; c < N * N; c++) {
    if (!sample.inMap[c]) continue;
    if (core[c]) { g[c] = core[c]; continue; }
    if (sample.luminance[c] >= threshold || sample.water[c]) {
      g[c] = sample.water[c] ? Region.RIVER : schem[c] === Region.LANE || schem[c] === Region.BASE || schem[c] === Region.PIT ? schem[c] : Region.JUNGLE;
    }
  }
  for (let pass = 0; pass < 2; pass++) {
    const h = g.slice();
    for (let j = 1; j < N - 1; j++) for (let i = 1; i < N - 1; i++) {
      const c = j * N + i;
      let w = 0;
      for (let dj = -1; dj <= 1; dj++) for (let di = -1; di <= 1; di++) if ((di || dj) && g[c + dj * N + di]) w++;
      if (!g[c] && w >= 6 && sample.inMap[c]) h[c] = schem[c] || Region.JUNGLE;
      else if (g[c] && !core[c] && w <= 1) h[c] = 0;
    }
    g = h;
  }
  const seen = new Uint8Array(N * N), stack: number[] = [];
  const [si, sj] = cellOf([7420, 7460]), s0 = idx(si, sj);
  if (g[s0]) { seen[s0] = 1; stack.push(s0); }
  while (stack.length) {
    const u = stack.pop()!, ui = u % N, uj = Math.floor(u / N);
    for (const [di, dj] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const vi = ui + di, vj = uj + dj;
      if (vi < 0 || vj < 0 || vi >= N || vj >= N) continue;
      const v = idx(vi, vj);
      if (g[v] && !seen[v]) { seen[v] = 1; stack.push(v); }
    }
  }
  for (let c = 0; c < N * N; c++) if (!seen[c]) g[c] = 0;
  return g;
}

/** Nearest walkable cell to a point. */
export function snapToGrid(grid: Uint8Array, p: Pt): number {
  const [i, j] = cellOf(p);
  if (grid[idx(i, j)]) return idx(i, j);
  for (let r = 1; r < 25; r++) for (let dj = -r; dj <= r; dj++) for (let di = -r; di <= r; di++) {
    const a = i + di, b = j + dj;
    if (a >= 0 && b >= 0 && a < N && b < N && grid[idx(a, b)]) return idx(a, b);
  }
  return idx(i, j);
}

// ---------------------------------------------------------------- movement speed
/** Riot soft caps: above 415 ×0.8, above 490 ×0.5, below 220 halved. */
export function softCapMS(raw: number) {
  if (raw > 490) return raw * 0.5 + 230;
  if (raw > 415) return raw * 0.8 + 83;
  if (raw < 220) return 110 + raw * 0.5;
  return raw;
}
export const BOOTS = [
  { value: 0, label: 'No boots (+0)' }, { value: 25, label: 'Boots of Speed (+25)' }, { value: 45, label: 'Tier 2 boots (+45)' },
  { value: 55, label: 'Boots of Swiftness (+55)' }, { value: 50, label: 'Tier 3 boots · mid quest (+50)' }, { value: 65, label: 'Swiftmarch · mid quest (+65)' },
];
export interface MoveSpeedInput { base: number; boots: number; flat: number; percent: number; jungleQuest: boolean }
export function moveSpeeds(m: MoveSpeedInput) {
  const raw = (m.base + m.boots + m.flat) * (1 + m.percent / 100);
  return { raw, lane: softCapMS(raw), jungle: softCapMS((m.base + m.boots + m.flat) * (1 + m.percent / 100 + (m.jungleQuest ? 0.08 : 0))) };
}

// ---------------------------------------------------------------- Dijkstra (16-neighbour, no corner cutting)
const NEIGHBOURS: [number, number][] = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1], [1, 2], [2, 1], [-1, 2], [-2, 1], [1, -2], [2, -1], [-1, -2], [-2, -1]];
export interface TravelField { dist: Float64Array; prev: Int32Array }
/** Seconds from `src` to every cell. Jungle, river and pit cells use the jungle speed. */
export function travelField(grid: Uint8Array, src: number, speed: { lane: number; jungle: number }): TravelField {
  const dist = new Float64Array(N * N).fill(Infinity), prev = new Int32Array(N * N).fill(-1);
  dist[src] = 0;
  const hk: number[] = [src], hv: number[] = [0];
  const swap = (a: number, b: number) => { [hk[a], hk[b]] = [hk[b], hk[a]]; [hv[a], hv[b]] = [hv[b], hv[a]]; };
  while (hk.length) {
    const u = hk[0], du = hv[0];
    const lk = hk.pop()!, lv = hv.pop()!;
    if (hk.length) {
      hk[0] = lk; hv[0] = lv;
      for (let c = 0; ;) { const l = 2 * c + 1, r = l + 1; let m = c; if (l < hk.length && hv[l] < hv[m]) m = l; if (r < hk.length && hv[r] < hv[m]) m = r; if (m === c) break; swap(m, c); c = m; }
    }
    if (du > dist[u]) continue;
    const ui = u % N, uj = Math.floor(u / N);
    for (const [di, dj] of NEIGHBOURS) {
      const vi = ui + di, vj = uj + dj;
      if (vi < 0 || vj < 0 || vi >= N || vj >= N) continue;
      const v = idx(vi, vj);
      if (!grid[v]) continue;
      const sx = Math.sign(di), sy = Math.sign(dj);
      if (Math.abs(di) === 1 && Math.abs(dj) === 1) { if (!grid[idx(ui + sx, uj)] || !grid[idx(ui, uj + sy)]) continue; }
      else if (Math.abs(di) === 2) { if (!grid[idx(ui + sx, uj)] || !grid[idx(ui + sx, uj + sy)]) continue; }
      else if (Math.abs(dj) === 2) { if (!grid[idx(ui, uj + sy)] || !grid[idx(ui + sx, uj + sy)]) continue; }
      const reg = grid[v], sp = reg === Region.JUNGLE || reg === Region.RIVER || reg === Region.PIT ? speed.jungle : speed.lane;
      const nd = du + (Math.hypot(di, dj) * CELL) / sp;
      if (nd < dist[v]) {
        dist[v] = nd; prev[v] = u;
        hk.push(v); hv.push(nd);
        for (let c = hk.length - 1; c > 0;) { const p = (c - 1) >> 1; if (hv[p] <= hv[c]) break; swap(p, c); c = p; }
      }
    }
  }
  return { dist, prev };
}
export function pathTo(field: TravelField, target: number): number[] {
  const out: number[] = [];
  for (let c = target; c >= 0; c = field.prev[c]) out.push(c);
  return out.reverse();
}
export function pathLength(path: number[]) {
  let L = 0;
  for (let k = 1; k < path.length; k++) { const a = cellCenter(path[k - 1]), b = cellCenter(path[k]); L += Math.hypot(a[0] - b[0], a[1] - b[1]); }
  return L;
}
