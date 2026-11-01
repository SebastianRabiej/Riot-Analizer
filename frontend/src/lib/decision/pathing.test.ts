import { describe, expect, it } from 'vitest';
import {
  N, cellCenter, minimapGrid, moveSpeeds, pathLength, pathTo, sampleMinimap, schematicGrid, snapToGrid, softCapMS, spotById, travelField,
} from './pathing';

const route = (from: string, to: string, grid = schematicGrid(), speed = { lane: 385, jungle: 385 }) => {
  const a = snapToGrid(grid, spotById(from)!.p), b = snapToGrid(grid, spotById(to)!.p);
  const f = travelField(grid, a, speed);
  const [ax, ay] = cellCenter(a), [bx, by] = cellCenter(b);
  return { t: f.dist[b], len: pathLength(pathTo(f, b)), straight: Math.hypot(ax - bx, ay - by) };
};

describe('movement speed', () => {
  it('applies Riot soft caps', () => {
    expect(softCapMS(385)).toBe(385);
    expect(softCapMS(450)).toBeCloseTo(443);
    expect(softCapMS(520)).toBeCloseTo(490);
    expect(moveSpeeds({ base: 340, boots: 45, flat: 0, percent: 0, jungleQuest: true }).jungle).toBeCloseTo(415.64); // 415.8 raw, just over the 415 soft cap
  });
});

describe('pathing on the schematic grid', () => {
  it('finds paths that are never shorter than the straight line', () => {
    for (const [a, b] of [['midc', 'botc'], ['b-Blue buff', 'bbot'], ['bf', 'baron'], ['topc', 'drag']]) {
      const r = route(a, b);
      expect(Number.isFinite(r.t)).toBe(true);
      expect(r.len).toBeGreaterThanOrEqual(r.straight * 0.99);
      expect(r.t).toBeCloseTo(r.len / 385, 0);
    }
  });
  it('makes jungle routes detour around walls', () => {
    const r = route('b-Blue buff', 'bbot');
    expect(r.len / r.straight).toBeGreaterThan(1.03);
  });
  it('is faster with more movement speed', () => {
    expect(route('midc', 'drag', schematicGrid(), { lane: 450, jungle: 450 }).t).toBeLessThan(route('midc', 'drag').t);
  });
});

describe('walls from a minimap image', () => {
  it('keeps lanes walkable and blocks dark terrain', () => {
    // A synthetic all-dark "minimap": only the always-walkable lane/river/base cores survive.
    const W = 128, data = new Uint8ClampedArray(W * W * 4);
    for (let k = 0; k < W * W; k++) { data[k * 4] = 20; data[k * 4 + 1] = 40; data[k * 4 + 2] = 25; data[k * 4 + 3] = 255; }
    const grid = minimapGrid(sampleMinimap(data, W, W), 80);
    const walkable = grid.reduce((n, v) => n + (v ? 1 : 0), 0);
    expect(walkable).toBeGreaterThan(0);
    expect(walkable).toBeLessThan(schematicGrid().reduce((n, v) => n + (v ? 1 : 0), 0));
    expect(Number.isFinite(route('midc', 'botc', grid).t)).toBe(true);
    expect(grid.length).toBe(N * N);
  });
});
