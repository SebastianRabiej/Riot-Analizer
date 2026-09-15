import type { ReactNode } from 'react';

export type ObjKind = 'Dragon' | 'Baron' | 'Herald' | 'Grubs' | 'Objective';

export function dragonElement(sub: string | null): string {
  const s = (sub ?? '').toUpperCase();
  if (s.includes('FIRE') || s.includes('INFERNAL')) return 'fire';
  if (s.includes('WATER') || s.includes('OCEAN')) return 'water';
  if (s.includes('AIR') || s.includes('CLOUD')) return 'air';
  if (s.includes('EARTH') || s.includes('MOUNTAIN')) return 'earth';
  if (s.includes('HEXTECH')) return 'hextech';
  if (s.includes('CHEMTECH')) return 'chemtech';
  if (s.includes('ELDER')) return 'elder';
  return 'dragon';
}

const ELEMENT_COLOR: Record<string, string> = {
  fire: '#f0663c', water: '#3fa9e0', air: '#9fbdd0', earth: '#c2913f',
  hextech: '#46c8c8', chemtech: '#8fbf3f', elder: '#b98cff', dragon: '#e9b64c',
};
const KIND_COLOR: Record<string, string> = {
  Baron: '#7c4dff', Herald: '#b34fd6', Grubs: '#6f6bd6', Objective: '#e9b64c',
};

export function objectiveColor(kind: ObjKind, element?: string | null): string {
  if (kind === 'Dragon') return ELEMENT_COLOR[element ?? 'dragon'] ?? ELEMENT_COLOR.dragon;
  return KIND_COLOR[kind] ?? KIND_COLOR.Objective;
}

// White motif on a 0..24 grid. Herald pupil is cut with the badge colour.
function Motif({ kind, color }: { kind: ObjKind; color: string }): ReactNode {
  switch (kind) {
    case 'Baron':
      return (
        <>
          <circle cx={9.4} cy={7.2} r={1.5} fill="#fff" />
          <circle cx={14.6} cy={7.2} r={1.5} fill="#fff" />
          <path d="M4.5 9 H19.5 L17.9 13.2 L16.1 10.3 L14.2 13.4 L12 10.2 L9.8 13.4 L7.9 10.3 L6.1 13.2 Z" fill="#fff" />
        </>
      );
    case 'Herald':
      return (
        <>
          <path d="M2.8 12 C7 6.8 17 6.8 21.2 12 C17 17.2 7 17.2 2.8 12 Z" fill="#fff" />
          <path d="M12 8.6 L15.3 12 L12 15.4 L8.7 12 Z" fill={color} />
          <circle cx={12} cy={12} r={1.15} fill="#fff" />
        </>
      );
    case 'Grubs':
      return (
        <>
          <g fill="#fff">
            <ellipse cx={7.6} cy={14} rx={3} ry={3.7} />
            <ellipse cx={12} cy={10.6} rx={3} ry={3.7} />
            <ellipse cx={16.4} cy={14} rx={3} ry={3.7} />
          </g>
          <g fill="#0b0d12">
            <circle cx={7.6} cy={12.6} r={0.8} />
            <circle cx={12} cy={9.2} r={0.8} />
            <circle cx={16.4} cy={12.6} r={0.8} />
          </g>
          <g stroke={color} strokeWidth={0.9} opacity={0.55}>
            <path d="M6 15 H9.2" />
            <path d="M10.4 11.6 H13.6" />
            <path d="M14.8 15 H18" />
          </g>
        </>
      );
    case 'Dragon':
      return (
        <>
          <path d="M3.2 16.8 C7 11 12 8.4 21 6.6 C17.5 8.2 15.2 10 13.7 12.2 C15 12 16.4 12.2 17.6 12.7 C16 13.1 14.8 13.9 14 15 C14.7 15.2 15.4 15.6 15.9 16.2 C14.4 16.1 13 16.4 12 17.2 C11.2 15.9 9.6 15.3 8 15.4 C8.6 15.9 9 16.6 9.1 17.4 C7.2 16.4 5 16.2 3.2 16.8 Z" fill="#fff" />
          <circle cx={18.6} cy={8.2} r={1} fill="#0b0d12" />
        </>
      );
    default:
      return <path d="M12 3 L14 10 L21 12 L14 14 L12 21 L10 14 L3 12 L10 10 Z" fill="#fff" />;
  }
}

// A round objective badge, drawn as SVG elements — use inside an existing <svg> (e.g. the map).
export function MapObjectiveIcon({ cx, cy, size, kind, element }: {
  cx: number; cy: number; size: number; kind: ObjKind; element?: string | null;
}): ReactNode {
  const color = objectiveColor(kind, element);
  const r = size / 2;
  const scale = size / 24;
  return (
    <g transform={`translate(${cx - r}, ${cy - r})`}>
      <circle cx={r} cy={r} r={r - 0.6} fill="#0b0d12" opacity={0.62} />
      <circle cx={r} cy={r} r={r - 1.4} fill={color} opacity={0.3} />
      <circle cx={r} cy={r} r={r - 0.6} fill="none" stroke={color} strokeWidth={1.4} />
      <g transform={`translate(${r}, ${r}) scale(${scale}) translate(-12, -12)`}>
        <Motif kind={kind} color={color} />
      </g>
    </g>
  );
}

// A standalone objective icon (its own <svg>) — use in lists / inline text.
export function ObjectiveIcon({ kind, element, size = 20 }: {
  kind: ObjKind; element?: string | null; size?: number;
}): ReactNode {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" style={{ display: 'block', flexShrink: 0 }}>
      <MapObjectiveIcon cx={12} cy={12} size={24} kind={kind} element={element} />
    </svg>
  );
}
