import type { ReactNode } from 'react';
import { url, type U } from './clayRefs';

// the library's clay 3D art, drawn in a 120x120 box. the achievements and the stickers are made of these pieces

type Dir = 'v' | 'h' | 'd' | 'd2';
const DIRS: Record<Dir, [number, number, number, number]> = { v: [0, 0, 0, 1], h: [0, 0, 1, 0], d: [0, 0, 1, 1], d2: [1, 0, 0, 1] };

export function Lin({ id, c, dir = 'v' }: { id: string; c: string[]; dir?: Dir }) {
  const [x1, y1, x2, y2] = DIRS[dir];
  return (
    <linearGradient id={id} x1={x1} y1={y1} x2={x2} y2={y2}>
      {c.map((col, i) => (
        <stop key={i} offset={c.length === 1 ? 0 : i / (c.length - 1)} stopColor={col} />
      ))}
    </linearGradient>
  );
}

export function Rad({ id, c, cx = 0.36, cy = 0.32, r = 0.78 }: { id: string; c: string[]; cx?: number; cy?: number; r?: number }) {
  return (
    <radialGradient id={id} cx={cx} cy={cy} r={r} fx={cx} fy={cy}>
      {c.map((col, i) => (
        <stop key={i} offset={i / (c.length - 1)} stopColor={col} />
      ))}
    </radialGradient>
  );
}

// specular highlight
export function Shine({ u, cx, cy, rx, ry, rot = -30, o = 0.85 }: { u: U; cx: number; cy: number; rx: number; ry: number; rot?: number; o?: number }) {
  return <ellipse cx={cx} cy={cy} rx={rx} ry={ry} transform={`rotate(${rot} ${cx} ${cy})`} fill={url(u('gloss'))} opacity={o} />;
}

// four-point sparkle
export function Sparkle({ x, y, s = 6, fill = '#fff', o = 1, className }: { x: number; y: number; s?: number; fill?: string; o?: number; className?: string }) {
  const k = s * 0.2;
  return (
    <path
      className={className}
      d={`M${x} ${y - s} Q${x + k} ${y - k} ${x + s} ${y} Q${x + k} ${y + k} ${x} ${y + s} Q${x - k} ${y + k} ${x - s} ${y} Q${x - k} ${y - k} ${x} ${y - s} Z`}
      fill={fill}
      opacity={o}
    />
  );
}

export interface ClaySvgProps {
  u: U;
  size: number;
  // accessible name; "" when it's only decoration
  name: string;
  className?: string;
  // the group the object is drawn in, the part that moves
  objectClassName: string;
  shadowClassName: string;
  children: ReactNode;
}

// the box every clay drawing sits in: the shared gloss and rim, and a soft shadow under the object
export function ClaySvg({ u, size, name, className, objectClassName, shadowClassName, children }: ClaySvgProps) {
  return (
    <svg
      viewBox="0 0 120 120"
      width={size}
      height={size}
      className={className}
      role={name ? 'img' : undefined}
      aria-label={name || undefined}
      aria-hidden={name ? undefined : true}
    >
      <defs>
        <filter id={u('blur')} x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="3.2" />
        </filter>
        <radialGradient id={u('gloss')}>
          <stop offset="0" stopColor="#fff" stopOpacity=".95" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </radialGradient>
        <linearGradient id={u('rim')} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#fff" stopOpacity=".85" />
          <stop offset=".45" stopColor="#fff" stopOpacity="0" />
        </linearGradient>
      </defs>
      <ellipse className={shadowClassName} cx="60" cy="108" rx="30" ry="5" fill="rgba(30,20,80,.3)" filter={url(u('blur'))} />
      <g className={objectClassName}>{children}</g>
    </svg>
  );
}
