import type { ReactNode } from 'react';
import { cx } from '../utils/cx';
import { useSafeId } from '../utils/shared';
import { ACHIEVEMENTS_BY_ID, type AchievementId } from './catalog';
import './achievements.css';

/**
 * Hand-built "clay 3D" illustrations, one per achievement.
 * Each object is drawn in a 120×120 box with the same lighting recipe:
 * an extruded darker copy for thickness, a top-left light gradient,
 * a rim light on the upper edge, a specular highlight and a soft floating shadow.
 * Pure SVG: no images to host, crisp at any size, themeable and animatable.
 */

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

/** Specular highlight. */
function Shine({ u, cx, cy, rx, ry, rot = -30, o = 0.85 }: { u: U; cx: number; cy: number; rx: number; ry: number; rot?: number; o?: number }) {
  return <ellipse cx={cx} cy={cy} rx={rx} ry={ry} transform={`rotate(${rot} ${cx} ${cy})`} fill={url(u('gloss'))} opacity={o} />;
}

/** Four-point sparkle. */
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
  // ── Compass ────────────────────────────────────────────
  'know-yourself': (u) => (
    <>
      <defs>
        <Lin id={u('a')} c={['#aea5ff', '#5b4fe0']} dir="d" />
        <Lin id={u('side')} c={['#4a3fc9', '#2a2096']} />
        <Rad id={u('dial')} c={['#ffffff', '#f1efff', '#d6d0ff']} cx={0.4} cy={0.35} r={0.85} />
        <Lin id={u('n')} c={['#ff9282', '#e5322d']} />
        <Lin id={u('s')} c={['#ffffff', '#c5c0e6']} />
        <Rad id={u('pin')} c={['#fff6c4', '#ffc93c', '#c98200']} />
      </defs>
      <circle cx="60" cy="62" r="38" fill={url(u('side'))} />
      <circle cx="60" cy="55" r="38" fill={url(u('a'))} stroke={url(u('rim'))} strokeWidth="1.5" />
      <circle cx="60" cy="57.5" r="29.5" fill="#2a2096" opacity=".35" />
      <circle cx="60" cy="56" r="28" fill={url(u('dial'))} />
      {[0, 90, 180, 270].map((a) => (
        <rect key={a} x="58.5" y="30.5" width="3" height="7" rx="1.5" fill="#b3abff" transform={`rotate(${a} 60 56)`} />
      ))}
      {[45, 135, 225, 315].map((a) => (
        <circle key={a} cx="60" cy="33" r="1.4" fill="#cfc9ff" transform={`rotate(${a} 60 56)`} />
      ))}
      <g transform="rotate(35 60 56)">
        <path d="M60 33 L67 56 L53 56 Z" fill={url(u('n'))} />
        <path d="M60 79 L67 56 L53 56 Z" fill={url(u('s'))} />
        <path d="M60 33 L60 79 L53 56 Z" fill="#000" opacity=".1" />
      </g>
      <circle cx="60" cy="56" r="5.2" fill={url(u('pin'))} />
      <circle cx="58.6" cy="54.6" r="1.6" fill="#fff" opacity=".9" />
      <Shine u={u} cx={42} cy={33} rx={13} ry={5.5} />
    </>
  ),

  // ── ID card with check ─────────────────────────────────
  'all-set': (u) => (
    <>
      <defs>
        <Lin id={u('a')} c={['#a6e0ff', '#2a9fd6']} dir="d" />
        <Lin id={u('chk')} c={['#86f5c6', '#13a06e']} dir="d" />
        <Rad id={u('ph')} c={['#ffffff', '#dcefff']} />
        <clipPath id={u('clip')}>
          <circle cx="40" cy="57" r="11" />
        </clipPath>
      </defs>
      <g transform="rotate(-6 60 64)">
        <rect x="18" y="41" width="84" height="56" rx="12" fill="#15628f" />
        <rect x="18" y="35" width="84" height="56" rx="12" fill={url(u('a'))} stroke={url(u('rim'))} strokeWidth="1.5" />
        <circle cx="40" cy="57" r="11" fill={url(u('ph'))} />
        <g clipPath={url(u('clip'))}>
          <circle cx="40" cy="54" r="4.4" fill="#2a9fd6" />
          <ellipse cx="40" cy="66.5" rx="8" ry="6" fill="#2a9fd6" />
        </g>
        <rect x="58" y="48" width="32" height="6" rx="3" fill="#fff" opacity=".95" />
        <rect x="58" y="59" width="22" height="6" rx="3" fill="#fff" opacity=".6" />
        <rect x="28" y="75" width="62" height="6" rx="3" fill="#fff" opacity=".42" />
        <Shine u={u} cx={36} cy={40} rx={15} ry={3.6} rot={-4} o={0.65} />
      </g>
      <circle cx="93" cy="41" r="13" fill="#0b6f4a" />
      <circle cx="93" cy="36" r="13" fill={url(u('chk'))} stroke={url(u('rim'))} strokeWidth="1.2" />
      <path d="M87 36.5 l4.2 4.2 l8.3 -8.7" stroke="#fff" strokeWidth="3.6" fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <Shine u={u} cx={88} cy={29} rx={5} ry={2.4} o={0.7} />
    </>
  ),

  // ── Speech bubbles ─────────────────────────────────────
  polyglot: (u) => {
    const back = 'M60 20 H84 a16 16 0 0 1 16 16 V44 a16 16 0 0 1 -12 15.5 L97 72 L78 60 H60 a16 16 0 0 1 -16 -16 V36 a16 16 0 0 1 16 -16 Z';
    const front = 'M36 46 H64 a16 16 0 0 1 16 16 V70 a16 16 0 0 1 -16 16 H43 L25 98 L30 84.5 a16 16 0 0 1 -10 -14.5 V62 a16 16 0 0 1 16 -16 Z';
    return (
      <>
        <defs>
          <Lin id={u('b')} c={['#b6adff', '#6457ec']} dir="d" />
          <Lin id={u('f')} c={['#ffc4a3', '#eb6834']} dir="d" />
        </defs>
        <path d={back} fill="#3a2fb0" transform="translate(0 5)" />
        <path d={back} fill={url(u('b'))} stroke={url(u('rim'))} strokeWidth="1.3" />
        <text x="72" y="45.5" textAnchor="middle" fontFamily="var(--sm-font), sans-serif" fontWeight="600" fontSize="15" fill="#fff">
          Olá
        </text>
        <path d={front} fill="#a8401a" transform="translate(0 5)" />
        <path d={front} fill={url(u('f'))} stroke={url(u('rim'))} strokeWidth="1.3" />
        <text x="50" y="72" textAnchor="middle" fontFamily="var(--sm-font), sans-serif" fontWeight="600" fontSize="17" fill="#fff">
          Hi!
        </text>
        <Shine u={u} cx={58} cy={25} rx={10} ry={3} rot={-8} o={0.6} />
        <Shine u={u} cx={32} cy={52} rx={9} ry={3} rot={-20} o={0.7} />
      </>
    );
  },

  // ── Four spheres on a tray (the mark, in 3D) ───────────
  'team-player': (u) => {
    const balls: [number, number, string[]][] = [
      [45, 50, ['#c6c0ff', '#6c60f0', '#3a2fb0']],
      [75, 50, ['#bdeeff', '#2a9fd6', '#145f8c']],
      [45, 74, ['#ffd0b3', '#eb6834', '#a33c16']],
      [75, 74, ['#aef7d6', '#1baf7a', '#0b6f4a']],
    ];
    return (
      <>
        <defs>
          <Lin id={u('tray')} c={['#4c4585', '#15123a']} dir="d" />
          {balls.map((b, i) => (
            <Rad key={i} id={u(`b${i}`)} c={b[2]} cx={0.34} cy={0.3} r={0.8} />
          ))}
        </defs>
        <rect x="21" y="35" width="78" height="66" rx="24" fill="#06051a" />
        <rect x="21" y="28" width="78" height="66" rx="24" fill={url(u('tray'))} stroke={url(u('rim'))} strokeWidth="1.2" />
        {balls.map(([x, y], i) => (
          <g key={i}>
            <ellipse cx={x} cy={y + 9} rx="10" ry="3" fill="#000" opacity=".45" filter={url(u('blur'))} />
            <circle cx={x} cy={y} r="12.5" fill={url(u(`b${i}`))} />
            <ellipse cx={x - 4} cy={y - 5} rx="4" ry="2.6" transform={`rotate(-30 ${x - 4} ${y - 5})`} fill="#fff" opacity=".8" />
          </g>
        ))}
        <Shine u={u} cx={36} cy={33} rx={12} ry={3} rot={-10} o={0.35} />
      </>
    );
  },

  // ── Puzzle piece ───────────────────────────────────────
  'squad-veteran': (u) => {
    // A classic jigsaw piece: knobs (with a neck) on top and right, sockets on the left and bottom.
    const piece = (fill: string, holes: string) => (
      <>
        <rect x="32" y="40" width="50" height="50" rx="8" fill={fill} />
        <rect x="52" y="30" width="10" height="12" fill={fill} />
        <circle cx="57" cy="28" r="9.5" fill={fill} />
        <rect x="80" y="60" width="12" height="10" fill={fill} />
        <circle cx="94" cy="65" r="9.5" fill={fill} />
        <rect x="30" y="60" width="10" height="10" fill={holes} />
        <circle cx="42" cy="65" r="8.5" fill={holes} />
        <rect x="52" y="82" width="10" height="10" fill={holes} />
        <circle cx="57" cy="80" r="8.5" fill={holes} />
      </>
    );
    return (
      <>
        <defs>
          <Lin id={u('a')} c={['#b2f9dd', '#22c286', '#14966a']} dir="d" />
          <Lin id={u('b')} c={['#ffe68a', '#f2a100']} dir="d" />
          <mask id={u('m')} maskUnits="userSpaceOnUse" x="0" y="0" width="120" height="120">
            {piece('#fff', '#000')}
          </mask>
        </defs>
        <g transform="rotate(-12 62 62)">
          <g transform="translate(0 6)">
            <rect x="10" y="8" width="100" height="100" fill="#0b6f4a" mask={url(u('m'))} />
          </g>
          <rect x="10" y="8" width="100" height="100" fill={url(u('a'))} mask={url(u('m'))} />
          <Shine u={u} cx={48} cy={48} rx={11} ry={4} o={0.75} />
          <Shine u={u} cx={54} cy={24} rx={4.5} ry={2.2} o={0.8} />
          <Shine u={u} cx={92} cy={61} rx={4} ry={2} o={0.7} />
        </g>
        <g transform="translate(14 76) rotate(18)">
          <rect x="0" y="4" width="20" height="20" rx="4" fill="#b87700" />
          <rect x="0" y="0" width="20" height="20" rx="4" fill={url(u('b'))} />
          <circle cx="24" cy="10" r="5" fill={url(u('b'))} />
        </g>
        <Sparkle x={100} y={28} s={7} fill="#1baf7a" className="sm-ach-twinkle" />
        <Sparkle x={24} y={34} s={4} fill="#7a6bff" className="sm-ach-twinkle sm-ach-twinkle--2" />
      </>
    );
  },

  // ── Paper plane ────────────────────────────────────────
  'new-faces': (u) => (
    <>
      <defs>
        <Lin id={u('top')} c={['#ffffff', '#dcd6fb']} dir="d" />
        <Lin id={u('fold')} c={['#aca2ff', '#5d50ea']} dir="d" />
      </defs>
      <path d="M12 96 C 24 92, 30 80, 44 74" stroke="#8f82ff" strokeWidth="3" strokeDasharray="1 7" strokeLinecap="round" fill="none" />
      <circle cx="16" cy="93" r="4" fill="#eb6834" />
      <circle cx="28" cy="86" r="4" fill="#1baf7a" />
      <circle cx="38" cy="78" r="4" fill="#2a9fd6" />
      <polygon points="20,60 104,26 53,70" fill="#c3bdee" transform="translate(0 4)" />
      <polygon points="20,60 104,26 53,70" fill={url(u('top'))} stroke={url(u('rim'))} strokeWidth="1" />
      <polygon points="53,70 104,26 66,94" fill={url(u('fold'))} />
      <polygon points="53,70 66,94 59,76" fill="#3a2fb0" />
      <line x1="53" y1="70" x2="104" y2="26" stroke="#fff" strokeWidth="1.2" opacity=".75" />
      <Sparkle x={98} y={60} s={6} fill="#ffc93c" />
    </>
  ),

  // ── Door with light ────────────────────────────────────
  'door-opener': (u) => (
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

  // ── Coffee cup at sunrise ──────────────────────────────
  'early-bird': (u) => (
    <>
      <defs>
        <Rad id={u('sun')} c={['#fff6c4', '#ffc93c', '#f29a00']} />
        <Lin id={u('cup')} c={['#ffc9de', '#ff7aab', '#d23d78']} dir="h" />
        <Lin id={u('saucer')} c={['#ffffff', '#e4e0f8']} />
        <Lin id={u('coffee')} c={['#9a6644', '#4a2a18']} />
      </defs>
      {Array.from({ length: 8 }).map((_, i) => (
        <line key={i} x1="92" y1="11" x2="92" y2="7" stroke="#ffc93c" strokeWidth="2.6" strokeLinecap="round" transform={`rotate(${i * 45} 92 26)`} />
      ))}
      <circle cx="92" cy="26" r="10" fill={url(u('sun'))} />
      <path d="M50 40 C 44 33, 56 28, 50 19" stroke="#cfc9ff" strokeWidth="4" strokeLinecap="round" fill="none" />
      <path d="M66 40 C 60 33, 72 28, 66 19" stroke="#cfc9ff" strokeWidth="4" strokeLinecap="round" fill="none" opacity=".75" />
      <ellipse cx="60" cy="97" rx="40" ry="10" fill="#c3bce6" />
      <ellipse cx="60" cy="93" rx="40" ry="10" fill={url(u('saucer'))} />
      <ellipse cx="60" cy="92" rx="24" ry="5" fill="#d8d2f5" />
      <path d="M85 58 C 100 56, 101 78, 82 79" stroke="#e8558f" strokeWidth="6.5" strokeLinecap="round" fill="none" />
      <path d="M33 50 C 34 70, 37 85, 47 89 C 54 92, 66 92, 73 89 C 83 85, 86 70, 87 50 Z" fill={url(u('cup'))} />
      <ellipse cx="60" cy="50" rx="27" ry="7" fill="#ffdce9" />
      <ellipse cx="60" cy="51" rx="22.5" ry="5" fill={url(u('coffee'))} />
      <ellipse cx="56" cy="50" rx="8" ry="1.6" fill="#c98f62" opacity=".55" />
      <Shine u={u} cx={42} cy={68} rx={3.4} ry={12} rot={-6} o={0.75} />
    </>
  ),

  // ── Crescent moon ──────────────────────────────────────
  'night-owl': (u) => (
    <>
      <defs>
        <Lin id={u('a')} c={['#fff6c4', '#ffd23f', '#f29a00']} dir="d" />
        <mask id={u('m')} maskUnits="userSpaceOnUse" x="0" y="0" width="120" height="120">
          <circle cx="56" cy="57" r="34" fill="#fff" />
          <circle cx="75" cy="42" r="29" fill="#000" />
        </mask>
      </defs>
      <g transform="translate(0 5)">
        <rect x="0" y="0" width="120" height="120" fill="#a86300" mask={url(u('m'))} />
      </g>
      <g mask={url(u('m'))}>
        <rect x="20" y="22" width="74" height="72" fill={url(u('a'))} />
        <circle cx="40" cy="65" r="5" fill="#e09000" opacity=".45" />
        <circle cx="53" cy="81" r="3.6" fill="#e09000" opacity=".45" />
        <circle cx="33" cy="49" r="3" fill="#e09000" opacity=".4" />
        <Shine u={u} cx={34} cy={40} rx={5} ry={12} rot={25} o={0.8} />
      </g>
      <Sparkle x={90} y={80} s={8} fill="#ffffff" className="sm-ach-twinkle" />
      <Sparkle x={98} y={28} s={5} fill="#d9d0ff" className="sm-ach-twinkle sm-ach-twinkle--2" />
      <Sparkle x={76} y={100} s={4} fill="#ffe27a" className="sm-ach-twinkle sm-ach-twinkle--3" />
      <circle cx="18" cy="26" r="1.6" fill="#fff" opacity=".7" />
      <circle cx="104" cy="56" r="1.4" fill="#fff" opacity=".6" />
    </>
  ),

  // ── Stack of books with an apple ───────────────────────
  bookworm: (u) => (
    <>
      <defs>
        <Lin id={u('b1')} c={['#9d92ff', '#4a3df5']} />
        <Lin id={u('b2')} c={['#ffc4a3', '#eb6834']} />
        <Lin id={u('b3')} c={['#a8f7d6', '#1baf7a']} />
        <Rad id={u('apple')} c={['#ffb3a6', '#e5322d', '#9e1a17']} />
      </defs>
      <rect x="17" y="83" width="86" height="18" rx="5" fill="#2a2096" />
      <rect x="17" y="78" width="86" height="18" rx="5" fill={url(u('b1'))} stroke={url(u('rim'))} strokeWidth="1" />
      <rect x="95" y="80" width="6" height="14" rx="2" fill="#fff8e6" />
      <rect x="29" y="83" width="24" height="8" rx="2" fill="#fff" opacity=".85" />
      <g transform="rotate(-5 60 68)">
        <rect x="23" y="65" width="74" height="16" rx="5" fill="#a33c16" />
        <rect x="23" y="60" width="74" height="16" rx="5" fill={url(u('b2'))} stroke={url(u('rim'))} strokeWidth="1" />
        <rect x="25" y="62" width="6" height="12" rx="2" fill="#fff8e6" />
        <rect x="62" y="64" width="24" height="8" rx="2" fill="#fff" opacity=".8" />
      </g>
      <g transform="rotate(4 60 50)">
        <rect x="21" y="49" width="76" height="16" rx="5" fill="#0b6f4a" />
        <rect x="21" y="44" width="76" height="16" rx="5" fill={url(u('b3'))} stroke={url(u('rim'))} strokeWidth="1" />
        <rect x="89" y="46" width="6" height="12" rx="2" fill="#fff8e6" />
        <rect x="30" y="48" width="18" height="8" rx="2" fill="#fff" opacity=".8" />
      </g>
      <ellipse cx="65" cy="44" rx="9" ry="2.4" fill="#000" opacity=".2" />
      <circle cx="65" cy="33" r="9.5" fill={url(u('apple'))} />
      <path d="M65 24 C 65 21, 66 19, 68 17" stroke="#6b3a1e" strokeWidth="2" strokeLinecap="round" fill="none" />
      <path d="M67 21 C 72 16, 78 18, 79 20 C 74 23, 70 23, 67 21 Z" fill="#1baf7a" />
      <ellipse cx="61.5" cy="29.5" rx="2.6" ry="1.6" fill="#fff" opacity=".8" transform="rotate(-30 61.5 29.5)" />
    </>
  ),

  // ── Graduation cap ─────────────────────────────────────
  mentor: (u) => (
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

  // ── Ticket ─────────────────────────────────────────────
  'full-house': (u) => (
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
        <text x="47" y="65" textAnchor="middle" fontFamily="var(--sm-font), sans-serif" fontWeight="700" fontSize="19" fill="#fff" letterSpacing=".5">
          FULL
        </text>
        <text x="47" y="76" textAnchor="middle" fontFamily="var(--sm-mono), monospace" fontSize="7.5" fill="#fff" opacity=".85" letterSpacing="1.5">
          HOUSE
        </text>
        <Sparkle x={92} y={62} s={8} fill="#fff" />
        <Shine u={u} cx={36} cy={44} rx={14} ry={3} rot={0} o={0.6} />
      </g>
    </>
  ),

  // ── Star ───────────────────────────────────────────────
  'five-stars': (u) => (
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

  // ── Heart with a speech bubble ─────────────────────────
  'kind-words': (u) => {
    const heart = 'M60 94 C 22 70, 18 34, 42 31 C 52 30, 58 37, 60 42 C 62 37, 68 30, 78 31 C 102 34, 98 70, 60 94 Z';
    return (
      <>
        <defs>
          <Lin id={u('a')} c={['#ffb8d6', '#ff4f8e', '#d92a6a']} dir="d" />
        </defs>
        <path d={heart} fill="#a81f55" transform="translate(0 6)" />
        <path d={heart} fill={url(u('a'))} stroke={url(u('rim'))} strokeWidth="1.4" />
        <Shine u={u} cx={40} cy={44} rx={8} ry={4.5} rot={-40} o={0.9} />
        <g transform="translate(70 12)">
          <rect x="0" y="5" width="36" height="22" rx="11" fill="#cdc6ee" />
          <rect x="0" y="0" width="36" height="22" rx="11" fill="#fff" />
          <path d="M8 20 L5 28 L14 21 Z" fill="#fff" />
          <circle cx="11" cy="11" r="2.4" fill="#ff4f8e" />
          <circle cx="18" cy="11" r="2.4" fill="#ff4f8e" opacity=".7" />
          <circle cx="25" cy="11" r="2.4" fill="#ff4f8e" opacity=".45" />
        </g>
      </>
    );
  },

  // ── Calendar with check ────────────────────────────────
  'never-missed': (u) => (
    <>
      <defs>
        <Lin id={u('page')} c={['#ffffff', '#ebe8f8']} />
        <Lin id={u('head')} c={['#ffa184', '#e5322d']} />
        <Lin id={u('ring')} c={['#5d55a0', '#1f1a45']} dir="h" />
        <Lin id={u('chk')} c={['#86f5c6', '#13a06e']} dir="d" />
      </defs>
      <rect x="20" y="32" width="80" height="68" rx="14" fill="#b9b2da" />
      <rect x="20" y="26" width="80" height="68" rx="14" fill={url(u('page'))} />
      <path d="M34 26 H86 a14 14 0 0 1 14 14 V46 H20 V40 a14 14 0 0 1 14 -14 Z" fill={url(u('head'))} />
      <Shine u={u} cx={36} cy={31} rx={11} ry={2.4} rot={0} o={0.6} />
      <rect x="38" y="17" width="7" height="17" rx="3.5" fill={url(u('ring'))} />
      <rect x="75" y="17" width="7" height="17" rx="3.5" fill={url(u('ring'))} />
      {Array.from({ length: 15 }).map((_, i) => {
        const c = i % 5;
        const r = Math.floor(i / 5);
        return <rect key={i} x={30 + c * 13} y={53 + r * 12} width="7" height="6" rx="2" fill={r === 2 && c > 2 ? 'transparent' : '#d9d5ef'} />;
      })}
      <circle cx="78" cy="83" r="15" fill="#0b6f4a" />
      <circle cx="78" cy="78" r="15" fill={url(u('chk'))} stroke={url(u('rim'))} strokeWidth="1.2" />
      <path d="M71 78.5 l4.8 4.8 l9.4 -9.8" stroke="#fff" strokeWidth="4" fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <Shine u={u} cx={72} cy={70} rx={5} ry={2.4} o={0.7} />
    </>
  ),

  // ── Rocket ─────────────────────────────────────────────
  'top-ten': (u) => (
    <>
      <defs>
        <Lin id={u('body')} c={['#ffffff', '#e8e5f8', '#b3addb']} dir="h" />
        <Lin id={u('red')} c={['#ffa184', '#d92b26']} dir="d" />
        <Lin id={u('flame')} c={['#fff1a8', '#ff9a2a', '#e5322d']} />
        <Rad id={u('glass')} c={['#d6f4ff', '#55bdf0', '#1a6fa5']} />
      </defs>
      <line x1="16" y1="80" x2="30" y2="70" stroke="#b9b2ff" strokeWidth="3" strokeLinecap="round" />
      <line x1="22" y1="96" x2="38" y2="84" stroke="#b9b2ff" strokeWidth="3" strokeLinecap="round" opacity=".7" />
      <g transform="rotate(40 60 60)">
        <path className="sm-ach-flame" d="M50 80 C 51 96, 58 106, 60 112 C 62 106, 69 96, 70 80 Z" fill={url(u('flame'))} />
        <path d="M45 64 L29 88 L46 83 Z" fill={url(u('red'))} />
        <path d="M75 64 L91 88 L74 83 Z" fill={url(u('red'))} />
        <path d="M60 13 C 77 27, 81 52, 75 82 H45 C 39 52, 43 27, 60 13 Z" fill={url(u('body'))} stroke={url(u('rim'))} strokeWidth="1" />
        <path d="M60 13 C 67 19, 71 25, 73.5 31 H46.5 C 49 25, 53 19, 60 13 Z" fill={url(u('red'))} />
        <circle cx="60" cy="49" r="10" fill="#4a3df5" />
        <circle cx="60" cy="49" r="7" fill={url(u('glass'))} />
        <ellipse cx="57.5" cy="46.5" rx="2.4" ry="1.5" fill="#fff" opacity=".85" transform="rotate(-30 57.5 46.5)" />
        <rect x="46" y="80" width="28" height="5" rx="2.5" fill="#8a84b5" />
        <Shine u={u} cx={51} cy={62} rx={2.8} ry={12} rot={0} o={0.8} />
      </g>
      <Sparkle x={100} y={22} s={6} fill="#ffc93c" className="sm-ach-twinkle" />
    </>
  ),

  // ── Trophy ─────────────────────────────────────────────
  'clean-sweep': (u) => (
    <>
      <defs>
        <Lin id={u('cup')} c={['#fff3b0', '#ffc93c', '#d48600']} dir="h" />
        <Lin id={u('gold')} c={['#ffd23f', '#c27c00']} />
        <Lin id={u('base')} c={['#5a5292', '#1a1640']} />
      </defs>
      <rect x="37" y="91" width="46" height="13" rx="5" fill="#07061a" />
      <rect x="37" y="86" width="46" height="13" rx="5" fill={url(u('base'))} stroke={url(u('rim'))} strokeWidth="1" />
      <rect x="50" y="90" width="20" height="5" rx="2" fill={url(u('gold'))} />
      <path d="M53 70 H67 L65 86 H55 Z" fill={url(u('gold'))} />
      <path d="M36 31 C 17 31, 17 58, 42 61" stroke="#e8a100" strokeWidth="6" fill="none" strokeLinecap="round" />
      <path d="M84 31 C 103 31, 103 58, 78 61" stroke="#c98200" strokeWidth="6" fill="none" strokeLinecap="round" />
      <path d="M33 24 H87 V40 C 87 60, 75 73, 60 73 C 45 73, 33 60, 33 40 Z" fill={url(u('cup'))} />
      <ellipse cx="60" cy="24" rx="27" ry="5.5" fill="#ffe68a" />
      <ellipse cx="60" cy="24.6" rx="22.5" ry="3.4" fill="#c98200" />
      <path d={STAR} fill="#fff" opacity=".5" transform="translate(60 47) scale(.32) translate(-60 -57)" />
      <Shine u={u} cx={42} cy={42} rx={3.6} ry={12} rot={-8} o={0.85} />
      <Sparkle x={98} y={20} s={6} fill="#ffc93c" className="sm-ach-twinkle" />
      <Sparkle x={18} y={74} s={4} fill="#7a6bff" className="sm-ach-twinkle sm-ach-twinkle--2" />
    </>
  ),

  // ── Crown ──────────────────────────────────────────────
  'category-master': (u) => {
    const crown = 'M22 84 L18 42 L42 60 L60 30 L78 60 L102 42 L98 84 Z';
    const tips: [number, number, number][] = [
      [18, 42, 6],
      [60, 30, 6.5],
      [102, 42, 6],
    ];
    return (
      <>
        <defs>
          <Lin id={u('a')} c={['#fff3b0', '#ffc93c', '#e39600']} dir="d" />
          <Lin id={u('band')} c={['#f2a100', '#b97400']} />
          <Rad id={u('tip')} c={['#fff6c4', '#ffc93c', '#c98200']} />
          <Rad id={u('g1')} c={['#e4dcff', '#7a6bff', '#2a2096']} />
          <Rad id={u('g2')} c={['#ffd6e6', '#ff4f8e', '#9e1a50']} />
          <Rad id={u('g3')} c={['#d4f3ff', '#2a9fd6', '#135a85']} />
        </defs>
        <path d={crown} fill="#a86300" stroke="#a86300" strokeWidth="6" strokeLinejoin="round" transform="translate(0 6)" />
        <path d={crown} fill={url(u('a'))} stroke="#ffcb45" strokeWidth="6" strokeLinejoin="round" />
        <rect x="22" y="72" width="76" height="15" rx="5" fill={url(u('band'))} />
        <circle cx="40" cy="79.5" r="4.6" fill={url(u('g1'))} />
        <circle cx="60" cy="79.5" r="6" fill={url(u('g2'))} />
        <circle cx="80" cy="79.5" r="4.6" fill={url(u('g3'))} />
        {tips.map(([x, y, r]) => (
          <g key={x}>
            <circle cx={x} cy={y} r={r} fill={url(u('tip'))} />
            <circle cx={x - 1.8} cy={y - 1.8} r={r * 0.32} fill="#fff" opacity=".85" />
          </g>
        ))}
        <Shine u={u} cx={40} cy={64} rx={4} ry={9} rot={-20} o={0.7} />
        <Sparkle x={104} y={20} s={7} fill="#ffc93c" className="sm-ach-twinkle" />
        <Sparkle x={14} y={22} s={5} fill="#7a6bff" className="sm-ach-twinkle sm-ach-twinkle--2" />
      </>
    );
  },

  // ── Diamond ────────────────────────────────────────────
  'perfect-score': (u) => (
    <>
      <defs>
        <Rad id={u('glow')} c={['rgba(255,255,255,.95)', 'rgba(255,255,255,0)']} cx={0.5} cy={0.5} r={0.5} />
        <Lin id={u('table')} c={['#ffffff', '#cdeeff']} />
        <Lin id={u('pl')} c={['#c4ebff', '#73b8f6']} dir="d" />
        <Lin id={u('pm')} c={['#94d2ff', '#4a8ff0']} />
        <Lin id={u('pr')} c={['#8d7bff', '#3d5fd6']} dir="d2" />
      </defs>
      <circle cx="60" cy="60" r="44" fill={url(u('glow'))} />
      <polygon points="22,52 38,34 82,34 98,52 60,98" fill="#2f3fa8" transform="translate(0 5)" opacity=".55" />
      <polygon points="22,52 38,34 50,52" fill="#f2fbff" />
      <polygon points="38,34 82,34 70,52 50,52" fill={url(u('table'))} />
      <polygon points="82,34 98,52 70,52" fill="#a6daff" />
      <polygon points="22,52 50,52 60,98" fill={url(u('pl'))} />
      <polygon points="50,52 70,52 60,98" fill={url(u('pm'))} />
      <polygon points="70,52 98,52 60,98" fill={url(u('pr'))} />
      <polyline points="22,52 98,52" stroke="#fff" strokeWidth="1.2" opacity=".8" />
      <polyline points="38,34 50,52 60,98 70,52 82,34" stroke="#fff" strokeWidth="1" fill="none" opacity=".6" />
      <polygon points="22,52 38,34 82,34 98,52 60,98" fill="none" stroke="#fff" strokeWidth="1.4" strokeLinejoin="round" opacity=".7" />
      <Shine u={u} cx={48} cy={41} rx={8} ry={2.6} rot={0} o={0.9} />
      <Sparkle x={96} y={26} s={9} fill="#ffffff" className="sm-ach-twinkle" />
      <Sparkle x={24} y={30} s={6} fill="#d9d0ff" className="sm-ach-twinkle sm-ach-twinkle--2" />
      <Sparkle x={92} y={86} s={5} fill="#a6daff" className="sm-ach-twinkle sm-ach-twinkle--3" />
    </>
  ),
};

export interface AchievementArtProps {
  id: AchievementId;
  size?: number;
  /** Greyed out, for achievements not earned yet. */
  locked?: boolean;
  /** Gentle floating loop (respects reduced motion). */
  animated?: boolean;
  /** Accessible name. Defaults to the achievement name. Pass "" for decorative use. */
  title?: string;
  className?: string;
}

/** The 3D illustration of one achievement. */
export function AchievementArt({ id, size = 120, locked, animated = true, title, className }: AchievementArtProps) {
  const uid = useSafeId('ach');
  const u: U = (n) => `${uid}-${n}`;
  const def = ACHIEVEMENTS_BY_ID[id];
  const name = title ?? def?.name ?? id;
  const draw = ART[id];
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
      <ellipse className="sm-ach-art__shadow" cx="60" cy="108" rx="30" ry="5" fill={def?.dark ? 'rgba(0,0,0,.6)' : 'rgba(30,20,80,.3)'} filter={url(u('blur'))} />
      <g className="sm-ach-art__object">{draw ? draw(u) : null}</g>
    </svg>
  );
}
