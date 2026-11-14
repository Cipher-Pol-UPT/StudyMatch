import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { cx } from '../utils/cx';
import { useSafeId } from '../utils/shared';
import './achievements.css';
import type { AchievementId } from './catalog';

// the library's clay 3D art, drawn in a 120x120 box: only the pieces our achievements use

type U = (name: string) => string;
const url = (id: string) => `url(#${id})`;

type Dir = 'v' | 'h' | 'd' | 'd2';
const DIRS: Record<Dir, [number, number, number, number]> = { v: [0, 0, 0, 1], h: [0, 0, 1, 0], d: [0, 0, 1, 1], d2: [1, 0, 0, 1] };

function Lin({ id, c, dir = 'v' }: { id: string; c: string[]; dir?: Dir }) {
  const [x1, y1, x2, y2] = DIRS[dir];
  return (
    <linearGradient id={id} x1={x1} y1={y1} x2={x2} y2={y2}>
      {c.map((col, i) => (
        <stop key={i} offset={c.length === 1 ? 0 : i / (c.length - 1)} stopColor={col} />
      ))}
    </linearGradient>
  );
}

function Rad({ id, c, cx = 0.36, cy = 0.32, r = 0.78 }: { id: string; c: string[]; cx?: number; cy?: number; r?: number }) {
  return (
    <radialGradient id={id} cx={cx} cy={cy} r={r} fx={cx} fy={cy}>
      {c.map((col, i) => (
        <stop key={i} offset={i / (c.length - 1)} stopColor={col} />
      ))}
    </radialGradient>
  );
}

// specular highlight
function Shine({ u, cx, cy, rx, ry, rot = -30, o = 0.85 }: { u: U; cx: number; cy: number; rx: number; ry: number; rot?: number; o?: number }) {
  return <ellipse cx={cx} cy={cy} rx={rx} ry={ry} transform={`rotate(${rot} ${cx} ${cy})`} fill={url(u('gloss'))} opacity={o} />;
}

// four-point sparkle
function Sparkle({ x, y, s = 6, fill = '#fff', o = 1, className }: { x: number; y: number; s?: number; fill?: string; o?: number; className?: string }) {
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

const STAR = 'M60 20 L69.99 44.25 L96.14 46.26 L76.17 63.25 L82.34 88.74 L60 75 L37.66 88.74 L43.83 63.25 L23.86 46.26 L50.01 44.25 Z';

const ART: Record<AchievementId, (u: U) => ReactNode> = {
  // an open door (the library's door-opener)
  FIRST_STUDYROOM: (u) => (
    <>
      <defs>
        <Lin id={u('frame')} c={['#ffc4a3', '#eb6834']} dir="d" />
        <Lin id={u('light')} c={['#fffdf0', '#ffe27a']} />
        <Lin id={u('leaf')} c={['#9a8fff', '#4a3df5']} dir="h" />
        <Lin id={u('beam')} c={['#ffe27a', 'rgba(255,226,122,0)']} />
        <Rad id={u('knob')} c={['#fff6c4', '#ffc93c', '#c98200']} />
      </defs>
      <polygon points="38,97 83,97 108,114 12,114" fill={url(u('beam'))} opacity=".7" />
      <rect x="28" y="23" width="64" height="81" rx="10" fill="#a33c16" />
      <rect x="28" y="16" width="64" height="81" rx="10" fill={url(u('frame'))} stroke={url(u('rim'))} strokeWidth="1.4" />
      <rect x="37" y="25" width="46" height="72" rx="5" fill={url(u('light'))} />
      <polygon points="58,31 83,25 83,97 58,91" fill="#fff" opacity=".45" />
      <polygon points="37,25 58,31 58,91 37,97" fill={url(u('leaf'))} />
      <line x1="58" y1="31" x2="58" y2="91" stroke="#c4bdff" strokeWidth="1.6" />
      <rect x="41" y="36" width="12" height="18" rx="2" fill="#fff" opacity=".14" transform="skewY(9)" />
      <circle cx="53" cy="62" r="2.8" fill={url(u('knob'))} />
      <Shine u={u} cx={38} cy={21} rx={10} ry={2.6} rot={-6} o={0.6} />
      <Sparkle x={98} y={36} s={6} fill="#ffc93c" />
    </>
  ),

  // a ticket with a heart (the library's full-house, without its english text)
  DEDICATED_TUTOR: (u) => (
    <>
      <defs>
        <Lin id={u('a')} c={['#ffadd0', '#d4498b']} dir="d" />
        <mask id={u('m')} maskUnits="userSpaceOnUse" x="0" y="0" width="120" height="120">
          <rect x="14" y="38" width="92" height="48" rx="10" fill="#fff" />
          <circle cx="14" cy="62" r="7" fill="#000" />
          <circle cx="106" cy="62" r="7" fill="#000" />
          <circle cx="78" cy="38" r="5" fill="#000" />
          <circle cx="78" cy="86" r="5" fill="#000" />
        </mask>
      </defs>
      <g transform="rotate(-10 60 62)">
        <g transform="translate(0 6)">
          <rect x="0" y="0" width="120" height="120" fill="#952a5d" mask={url(u('m'))} />
        </g>
        <rect x="14" y="38" width="92" height="48" fill={url(u('a'))} mask={url(u('m'))} />
        <line x1="78" y1="46" x2="78" y2="78" stroke="#fff" strokeWidth="2" strokeDasharray="2 4" strokeLinecap="round" opacity=".8" />
        <path d="M47 73 C 39 67, 34 62, 34 56.5 C 34 52.5, 37 49.5, 41 49.5 C 43.6 49.5, 45.7 51, 47 53 C 48.3 51, 50.4 49.5, 53 49.5 C 57 49.5, 60 52.5, 60 56.5 C 60 62, 55 67, 47 73 Z" fill="#fff" />
        <Sparkle x={92} y={62} s={8} fill="#fff" />
        <Shine u={u} cx={36} cy={44} rx={14} ry={3} rot={0} o={0.6} />
      </g>
    </>
  ),

  // a gold star (the library's five-stars)
  TOP_RATED: (u) => (
    <>
      <defs>
        <Lin id={u('a')} c={['#fff3b0', '#ffc93c', '#f29a00']} dir="d" />
      </defs>
      <path d={STAR} fill="#b06c00" stroke="#b06c00" strokeWidth="9" strokeLinejoin="round" transform="translate(0 6)" />
      <path d={STAR} fill={url(u('a'))} stroke="#ffcf4a" strokeWidth="9" strokeLinejoin="round" />
      <path d={STAR} fill="#fff" opacity=".22" transform="translate(60 57) scale(.55) translate(-60 -57)" />
      <Shine u={u} cx={47} cy={42} rx={8} ry={4} o={0.9} />
      <Sparkle x={100} y={24} s={7} fill="#ffc93c" className="sm-ach-twinkle" />
      <Sparkle x={18} y={86} s={5} fill="#7a6bff" className="sm-ach-twinkle sm-ach-twinkle--2" />
    </>
  ),

  // a graduation cap (the library's mentor)
  MENTOR: (u) => (
    <>
      <defs>
        <Lin id={u('skull')} c={['#4f4880', '#171338']} dir="h" />
        <Lin id={u('board')} c={['#625aa8', '#201b48']} dir="d" />
        <Lin id={u('gold')} c={['#ffe68a', '#f29a00']} />
      </defs>
      <path d="M36 54 V75 C 36 84, 84 84, 84 75 V54 Z" fill={url(u('skull'))} />
      <polygon points="60,31 106,51 60,71 14,51" fill="#07061a" />
      <polygon points="60,26 106,46 60,66 14,46" fill={url(u('board'))} stroke={url(u('rim'))} strokeWidth="1.2" strokeLinejoin="round" />
      <polygon points="60,31 94,46 60,61 26,46" fill="#fff" opacity=".06" />
      <path d="M60 46 L 97 51 V 73" stroke="#ffc93c" strokeWidth="2.6" fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="60" cy="46" r="4" fill={url(u('gold'))} />
      <path d="M92 71 H102 L104 89 C 100 92, 94 92, 90 89 Z" fill={url(u('gold'))} />
      <Shine u={u} cx={44} cy={38} rx={12} ry={3} rot={-22} o={0.45} />
      <Sparkle x={24} y={26} s={6} fill="#ffc93c" />
    </>
  ),
};

export interface AchievementArtProps {
  id: AchievementId;
  size?: number;
  // greyed out, for achievements not earned yet
  locked?: boolean;
  // gentle floating loop (respects reduced motion)
  animated?: boolean;
  // accessible name, the achievement's name by default; "" when it's only decoration
  title?: string;
  className?: string;
}

export function AchievementArt({ id, size = 120, locked, animated = true, title, className }: AchievementArtProps) {
  const { t } = useTranslation();
  const uid = useSafeId('ach');
  const u: U = (n) => `${uid}-${n}`;
  const name = title ?? t(`achievements.${id}.name`);
  return (
    <svg
      viewBox="0 0 120 120"
      width={size}
      height={size}
      className={cx('sm-ach-art', animated && 'sm-ach-art--float', locked && 'sm-ach-art--locked', className)}
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
      <ellipse className="sm-ach-art__shadow" cx="60" cy="108" rx="30" ry="5" fill="rgba(30,20,80,.3)" filter={url(u('blur'))} />
      <g className="sm-ach-art__object">{ART[id](u)}</g>
    </svg>
  );
}
