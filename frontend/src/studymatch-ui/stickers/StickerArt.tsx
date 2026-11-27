import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { ClaySvg, Lin, Rad, Shine, Sparkle } from '../achievements/Clay';
import { url, type U } from '../achievements/clayRefs';
import { cx } from '../utils/cx';
import { useSafeId } from '../utils/shared';
import type { StickerId } from './catalog';
import './stickers.css';

// StudyMatch's stickers (#215): Matchy, our clay blob, in a few moods and seasons, drawn with the achievements' pieces

const INK = '#2a1b5e';
const VIOLET = ['#d4ceff', '#8a7dff', '#4a3df5'];
const BLOB = 'M60 38 C 83 38, 95 54, 95 72 C 95 91, 80 100, 60 100 C 40 100, 25 91, 25 72 C 25 54, 37 38, 60 38 Z';
const HEART = 'M47 73 C 39 67, 34 62, 34 56.5 C 34 52.5, 37 49.5, 41 49.5 C 43.6 49.5, 45.7 51, 47 53 C 48.3 51, 50.4 49.5, 53 49.5 C 57 49.5, 60 52.5, 60 56.5 C 60 62, 55 67, 47 73 Z';
const EGG = 'M0 -15 C 9 -15, 13 -2, 13 4 C 13 12, 7 16, 0 16 C -7 16, -13 12, -13 4 C -13 -2, -9 -15, 0 -15 Z';

// the heart of the achievements' ticket, moved and sized
const heartAt = (x: number, y: number, s: number) => `translate(${x - 47 * s} ${y - 61 * s}) scale(${s})`;

// a star with n points, for an explosion
const burst = (cx: number, cy: number, outer: number, inner: number, n: number) => Array.from({ length: n * 2 }, (_, i) => {
  const r = i % 2 ? inner : outer;
  const a = (Math.PI * i) / n - Math.PI / 2;
  return `${(cx + r * Math.cos(a)).toFixed(1)},${(cy + r * Math.sin(a)).toFixed(1)}`;
}).join(' ');

// Matchy's body, with the darker clay under it
function Blob({ u, id = 'body', c = VIOLET, under = '#2e23a8', dy = 0 }: { u: U; id?: string; c?: string[]; under?: string; dy?: number }) {
  return (
    <g transform={dy ? `translate(0 ${dy})` : undefined}>
      <defs><Rad id={u(id)} c={c} /></defs>
      <path d={BLOB} fill={under} transform="translate(0 4)" />
      <path d={BLOB} fill={url(u(id))} stroke={url(u('rim'))} strokeWidth="1.2" />
      <Shine u={u} cx={44} cy={50} rx={9} ry={4.5} o={0.75} />
    </g>
  );
}

function Arm({ u, x, y, rot, id = 'body' }: { u: U; x: number; y: number; rot: number; id?: string }) {
  return <ellipse cx={x} cy={y} rx="6.5" ry="10" transform={`rotate(${rot} ${x} ${y})`} fill={url(u(id))} stroke={url(u('rim'))} strokeWidth="1" />;
}

type Eyes = 'open' | 'happy' | 'closed' | 'wink' | 'panic' | 'sleepy' | 'determined' | 'squint' | 'heart' | 'none';
type Mouth = 'smile' | 'open' | 'o' | 'wobbly' | 'cat' | 'smirk' | 'fangs' | 'grin' | 'none';

const line = (ink: string, width = 2.8) => ({ stroke: ink, strokeWidth: width, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, fill: 'none' });

function Eye({ kind, x, ink }: { kind: Exclude<Eyes, 'wink' | 'none'>; x: number; ink: string }) {
  const y = -4;
  switch (kind) {
    case 'happy': return <path d={`M${x - 5} ${y + 1.5} Q${x} ${y - 5} ${x + 5} ${y + 1.5}`} {...line(ink)} />;
    case 'squint': return <path d={x < 0 ? `M${x - 5} ${y - 4} L${x + 3} ${y} L${x - 5} ${y + 4}` : `M${x + 5} ${y - 4} L${x - 3} ${y} L${x + 5} ${y + 4}`} {...line(ink)} />;
    case 'heart': return <path d={HEART} transform={heartAt(x, y, 0.34)} fill="#ff4f8b" />;
    case 'closed': return <path d={`M${x - 5} ${y - 1} Q${x} ${y + 4} ${x + 5} ${y - 1}`} {...line(ink)} />;
    case 'panic': return (
      <>
        <circle cx={x} cy={y} r="6" fill="#fff" stroke={ink} strokeWidth="1.4" />
        <circle cx={x} cy={y + 0.6} r="1.8" fill={ink} />
      </>
    );
    case 'sleepy': return (
      <>
        <path d={`M${x - 5} ${y} Q${x} ${y + 3.5} ${x + 5} ${y}`} {...line(ink, 2.6)} />
        <path d={`M${x - 4} ${y + 5.5} Q${x} ${y + 7.5} ${x + 4} ${y + 5.5}`} {...line('#6b5fd6', 1.6)} />
      </>
    );
    default: return (
      <>
        {kind === 'determined' && <path d={x < 0 ? `M${x - 5} ${y - 9} L${x + 4} ${y - 6.5}` : `M${x + 5} ${y - 9} L${x - 4} ${y - 6.5}`} {...line(ink, 2.4)} />}
        <ellipse cx={x} cy={y} rx="3.8" ry="4.8" fill={ink} />
        <circle cx={x + 1.3} cy={y - 1.6} r="1.4" fill="#fff" />
      </>
    );
  }
}

function MouthShape({ kind, y, ink }: { kind: Mouth; y: number; ink: string }) {
  switch (kind) {
    case 'smile': return <path d={`M-6 ${y} Q0 ${y + 6} 6 ${y}`} {...line(ink, 2.6)} />;
    case 'cat': return <path d={`M-6 ${y} q3 4 6 0 q3 4 6 0`} {...line(ink, 2.4)} />;
    case 'smirk': return <path d={`M-5 ${y + 1} Q2 ${y + 5} 7 ${y - 2}`} {...line(ink, 2.6)} />;
    case 'wobbly': return <path d={`M-8 ${y + 2} q2 -3 4 0 t4 0 t4 0 t4 0`} {...line(ink, 2.2)} />;
    case 'o': return <ellipse cx="0" cy={y + 2} rx="3" ry="3.8" fill={ink} />;
    case 'fangs': return (
      <>
        <path d={`M-7 ${y - 1} Q0 ${y + 8} 7 ${y - 1} Z`} fill={ink} />
        <path d={`M-5 ${y - 0.6} L-3.2 ${y + 3.6} L-1.6 ${y - 0.4} Z M5 ${y - 0.6} L3.2 ${y + 3.6} L1.6 ${y - 0.4} Z`} fill="#fff" />
      </>
    );
    case 'open':
    case 'grin': {
      const w = kind === 'grin' ? 9 : 7;
      return (
        <>
          <path d={`M${-w} ${y - 1} Q0 ${y + 12} ${w} ${y - 1} Z`} fill={ink} />
          <ellipse cx="0" cy={y + 4.4} rx={w / 2} ry="2" fill="#ff7aa8" />
        </>
      );
    }
    default: return null;
  }
}

// Matchy's face, centred on x, y: its eyes, its mouth and the blush on its cheeks
function Face({ x = 60, y = 70, s = 1, eyes, mouth, blush = true, mouthY = 7, ink = INK }: { x?: number; y?: number; s?: number; eyes: Eyes; mouth: Mouth; blush?: boolean; mouthY?: number; ink?: string }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      {blush && <><ellipse cx="-19" cy="3" rx="5" ry="2.8" fill="#ff8fc0" opacity=".6" /><ellipse cx="19" cy="3" rx="5" ry="2.8" fill="#ff8fc0" opacity=".6" /></>}
      {eyes !== 'none' && <Eye kind={eyes === 'wink' ? 'open' : eyes} x={-12} ink={ink} />}
      {eyes !== 'none' && <Eye kind={eyes === 'wink' ? 'happy' : eyes} x={12} ink={ink} />}
      <MouthShape kind={mouth} y={mouthY} ink={ink} />
    </g>
  );
}

// bits of paper in the air, falling when the sticker moves
function Confetti() {
  return (
    <g className="sm-sticker__confetti">
      <rect x="12" y="30" width="6" height="3" rx="1" fill="#ff7aa8" transform="rotate(30 15 31)" />
      <rect x="100" y="34" width="6" height="3" rx="1" fill="#5fd3a0" transform="rotate(-25 103 35)" />
      <rect x="20" y="62" width="5" height="3" rx="1" fill="#ffc93c" transform="rotate(60 22 63)" />
      <rect x="98" y="66" width="5" height="3" rx="1" fill="#7fbfff" transform="rotate(-50 100 67)" />
    </g>
  );
}

function Egg({ u, id, x, y, s = 1, c, band = '#fff' }: { u: U; id: string; x: number; y: number; s?: number; c: string[]; band?: string }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <defs><Rad id={u(id)} c={c} /></defs>
      <path d={EGG} fill={url(u(id))} stroke={url(u('rim'))} strokeWidth="1" />
      <path d="M-12.6 2 l4.2 -4 l4.2 4 l4.2 -4 l4.2 4 l4.2 -4 l4.2 4" {...line(band, 2.2)} />
      <circle cx="-4" cy="-8" r="1.6" fill={band} />
      <circle cx="4" cy="10" r="1.6" fill={band} />
      <Shine u={u} cx={-5} cy={-6} rx={3} ry={5} rot={20} o={0.7} />
    </g>
  );
}

const ART: Record<StickerId, (u: U) => ReactNode> = {
  // every day

  HELLO: (u) => (
    <>
      <Arm u={u} x={29} y={84} rot={35} />
      <Blob u={u} />
      <g className="sm-sticker__wave">
        <Arm u={u} x={95} y={50} rot={-35} />
        <path d="M104 30 Q110 36 108 44 M110 24 Q118 32 116 44" {...line('#ffc93c', 2.6)} />
      </g>
      <Face eyes="happy" mouth="open" />
      <Sparkle x={18} y={40} s={7} fill="#ffc93c" className="sm-sticker__twinkle" />
    </>
  ),

  THANK_YOU: (u) => (
    <>
      <defs><Lin id={u('heart')} c={['#ffadd0', '#e0457f']} dir="d" /></defs>
      <Blob u={u} />
      <Face y={64} eyes="closed" mouth="smile" />
      <path d={HEART} transform={heartAt(60, 88, 1.15)} fill={url(u('heart'))} stroke={url(u('rim'))} strokeWidth="1" />
      <Shine u={u} cx={52} cy={82} rx={4} ry={2} o={0.8} />
      <Arm u={u} x={40} y={86} rot={-55} />
      <Arm u={u} x={80} y={86} rot={55} />
      <g className="sm-sticker__twinkle"><path d={HEART} transform={heartAt(100, 34, 0.4)} fill="#ff7aa8" /></g>
      <g className="sm-sticker__twinkle sm-sticker__twinkle--2"><path d={HEART} transform={heartAt(18, 46, 0.28)} fill="#ffadd0" /></g>
    </>
  ),

  ON_IT: (u) => (
    <>
      <defs>
        <Lin id={u('wood')} c={['#ffe27a', '#f29a00']} dir="h" />
      </defs>
      <Blob u={u} />
      <Face x={54} eyes="determined" mouth="smirk" />
      <g transform="rotate(28 90 66)">
        <rect x="84" y="30" width="12" height="8" rx="2.5" fill="#ff9ab8" />
        <rect x="84" y="37" width="12" height="4" fill="#c9c2ff" />
        <rect x="84" y="41" width="12" height="44" fill={url(u('wood'))} />
        <polygon points="84,85 96,85 90,97" fill="#ffd9a8" />
        <polygon points="88.2,93.4 91.8,93.4 90,97" fill={INK} />
      </g>
      <Arm u={u} x={84} y={76} rot={-20} />
      <path d="M10 60 H20 M8 70 H22 M12 80 H20" {...line('#b8b0ff', 2.4)} />
      <Sparkle x={104} y={26} s={6} fill="#ffc93c" className="sm-sticker__twinkle" />
    </>
  ),

  COFFEE_BREAK: (u) => (
    <>
      <defs><Lin id={u('mug')} c={['#ffffff', '#d9d4ff']} /></defs>
      <Blob u={u} />
      <Face x={52} y={66} eyes="sleepy" mouth="cat" />
      <path d="M70 62 q-4 -6 0 -12 t0 -12 M80 62 q-4 -6 0 -12 t0 -12" {...line('#b8b0ff', 2.4)} className="sm-sticker__steam" />
      <path d="M92 76 C 103 76, 103 93, 92 93" {...line('#c4bdff', 5)} />
      <rect x="62" y="68" width="32" height="30" rx="7" fill={url(u('mug'))} stroke={url(u('rim'))} strokeWidth="1" />
      <ellipse cx="78" cy="69.5" rx="14" ry="3.2" fill="#7a4a2a" />
      <path d={HEART} transform={heartAt(78, 84, 0.3)} fill="#ff7aa8" />
      <Arm u={u} x={62} y={86} rot={-60} />
    </>
  ),

  // reactions, animated

  LAUGHING: (u) => (
    <>
      <path d="M14 30 l6 6 M22 22 l3 8 M106 30 l-6 6 M98 22 l-3 8" {...line('#ffc93c', 2.6)} />
      <g className="sm-sticker__laugh">
        <Blob u={u} />
        <Face eyes="squint" mouth="grin" />
        <Arm u={u} x={42} y={92} rot={-70} />
        <Arm u={u} x={78} y={92} rot={70} />
      </g>
      <g className="sm-sticker__fall"><path d="M38 62 C 34 68, 35 72, 39 72 C 43 72, 44 68, 38 62 Z" fill="#7fd3ff" /></g>
      <g className="sm-sticker__fall sm-sticker__fall--2"><path d="M82 62 C 78 68, 79 72, 83 72 C 87 72, 88 68, 82 62 Z" fill="#7fd3ff" /></g>
    </>
  ),

  MIND_BLOWN: (u) => (
    <>
      <defs>
        <Rad id={u('boom')} c={['#fff8c4', '#ffc93c', '#ff7a2a']} cx={0.5} cy={0.6} />
      </defs>
      <g className="sm-sticker__burst">
        <polygon points={burst(60, 26, 25, 13, 9)} fill={url(u('boom'))} />
        <polygon points={burst(60, 28, 12, 6, 7)} fill="#fff8c4" />
      </g>
      <Blob u={u} />
      <Face eyes="panic" mouth="o" blush={false} />
      <Sparkle x={16} y={22} s={6} fill="#ff7aa8" className="sm-sticker__twinkle" />
      <Sparkle x={104} y={20} s={6} fill="#7a6bff" className="sm-sticker__twinkle sm-sticker__twinkle--2" />
      <Sparkle x={100} y={56} s={4} fill="#ffc93c" className="sm-sticker__twinkle" />
    </>
  ),

  PARTY: (u) => (
    <>
      <defs>
        <Lin id={u('hat')} c={['#ffadd0', '#e0457f']} dir="d" />
      </defs>
      <g className="sm-sticker__bounce">
        <Arm u={u} x={24} y={56} rot={-35} />
        <Arm u={u} x={96} y={56} rot={35} />
        <Blob u={u} />
        <g transform="rotate(10 62 30)">
          <path d="M48 44 L62 10 L76 44 Z" fill={url(u('hat'))} />
          <path d="M53 33 L71 33 M57 23 L67 23" {...line('#ffe27a', 3)} />
          <circle cx="62" cy="9" r="4.5" fill="#ffc93c" />
        </g>
        <Face eyes="happy" mouth="open" />
      </g>
      <Confetti />
      <g className="sm-sticker__confetti sm-sticker__confetti--2">
        <rect x="34" y="8" width="5" height="3" rx="1" fill="#7a6bff" transform="rotate(20 36 9)" />
        <rect x="86" y="12" width="6" height="3" rx="1" fill="#ff7aa8" transform="rotate(-40 89 13)" />
      </g>
    </>
  ),

  LOVE: (u) => (
    <>
      <g className="sm-sticker__pulse">
        <Blob u={u} />
        <Face eyes="heart" mouth="smile" />
      </g>
      <g className="sm-sticker__rise"><path d={HEART} transform={heartAt(100, 38, 0.38)} fill="#ff7aa8" /></g>
      <g className="sm-sticker__rise sm-sticker__rise--2"><path d={HEART} transform={heartAt(20, 44, 0.3)} fill="#ff4f8b" /></g>
      <g className="sm-sticker__rise sm-sticker__rise--3"><path d={HEART} transform={heartAt(88, 18, 0.24)} fill="#ffadd0" /></g>
    </>
  ),

  // exams

  EXAM_PANIC: (u) => (
    <>
      <g className="sm-sticker__flutter"><g transform="rotate(-18 18 26)">
        <rect x="6" y="12" width="24" height="30" rx="3" fill="#fff" stroke="#d9d4ff" strokeWidth="1.4" />
        <path d="M11 20 H25 M11 26 H25 M11 32 H20" {...line('#c4bdff', 1.8)} />
      </g></g>
      <g className="sm-sticker__flutter sm-sticker__flutter--2"><g transform="rotate(16 102 22)">
        <rect x="90" y="8" width="24" height="30" rx="3" fill="#fff" stroke="#d9d4ff" strokeWidth="1.4" />
        <path d="M96 17 L108 29 M108 17 L96 29" {...line('#e0457f', 2.6)} />
      </g></g>
      <g className="sm-sticker__shake">
        <Arm u={u} x={22} y={64} rot={-55} />
        <Arm u={u} x={98} y={64} rot={55} />
        <Blob u={u} />
        <Face eyes="panic" mouth="wobbly" blush={false} />
      </g>
      <g className="sm-sticker__fall"><path d="M90 46 C 86 52, 88 56, 92 56 C 96 56, 98 52, 90 46 Z" fill="#7fd3ff" /></g>
      <g className="sm-sticker__fall sm-sticker__fall--2"><path d="M32 50 C 29 55, 30 58, 33 58 C 36 58, 37 55, 32 50 Z" fill="#7fd3ff" /></g>
    </>
  ),

  ALL_NIGHTER: (u) => (
    <>
      <defs><Lin id={u('moon')} c={['#fff3b0', '#ffc93c']} dir="d" /></defs>
      <path d="M26 10 A 14 14 0 1 0 38 34 A 11 11 0 1 1 26 10 Z" fill={url(u('moon'))} />
      <Sparkle x={44} y={14} s={4} fill="#ffc93c" className="sm-sticker__twinkle" />
      <g className="sm-sticker__rise"><path d="M84 20 h9 l-9 10 h9" {...line(INK, 2.2)} /></g>
      <g className="sm-sticker__rise sm-sticker__rise--2"><path d="M98 8 h6 l-6 7 h6" {...line(INK, 2.2)} /></g>
      <g className="sm-sticker__nod">
        <Blob u={u} />
        <Face x={52} eyes="sleepy" mouth="o" blush={false} />
      </g>
      <rect x="64" y="90" width="40" height="10" rx="2" fill="#5fd3a0" />
      <rect x="68" y="80" width="34" height="10" rx="2" fill="#ff8fc0" />
      <rect x="66" y="70" width="36" height="10" rx="2" fill="#7fbfff" />
      <path d="M70 75 H98 M72 85 H98 M68 95 H100" {...line('#fff', 1.4)} opacity=".7" />
    </>
  ),

  PASSED: (u) => (
    <>
      <g className="sm-sticker__bounce">
        <Arm u={u} x={24} y={58} rot={-35} />
        <Arm u={u} x={96} y={58} rot={35} />
        <g transform="rotate(-6 60 20)">
          <rect x="42" y="6" width="36" height="28" rx="3" fill="#fff" stroke="#d9d4ff" strokeWidth="1.4" />
          <path d="M51 20 l6 6 l12 -13" {...line('#22b573', 4)} />
        </g>
        <Blob u={u} dy={4} />
        <Face y={74} eyes="happy" mouth="grin" />
      </g>
      <Confetti />
      <Sparkle x={104} y={16} s={6} fill="#ffc93c" className="sm-sticker__twinkle" />
      <Sparkle x={14} y={14} s={5} fill="#ff7aa8" className="sm-sticker__twinkle sm-sticker__twinkle--2" />
    </>
  ),

  GRADUATED: (u) => (
    <>
      <defs>
        <Lin id={u('board')} c={['#625aa8', '#201b48']} dir="d" />
        <Lin id={u('gold')} c={['#ffe68a', '#f29a00']} />
        <Lin id={u('paper')} c={['#fffaf0', '#ffe3b8']} />
      </defs>
      <Blob u={u} />
      <path d="M44 40 V47 C 44 53, 76 53, 76 47 V40 Z" fill="#171338" />
      <polygon points="60,26 94,39 60,52 26,39" fill={url(u('board'))} stroke={url(u('rim'))} strokeWidth="1" strokeLinejoin="round" />
      <path d="M60 39 L 90 43 V 58" {...line('#ffc93c', 2.4)} />
      <path d="M86 57 H94 L95 68 C 92 70, 88 70, 85 68 Z" fill={url(u('gold'))} />
      <Face y={70} eyes="closed" mouth="smile" />
      <rect x="34" y="84" width="52" height="13" rx="6.5" fill={url(u('paper'))} stroke="#f0cf9a" strokeWidth="1" />
      <rect x="57" y="83" width="6" height="15" rx="1.5" fill="#e0457f" />
      <Arm u={u} x={34} y={90} rot={-70} />
      <Arm u={u} x={86} y={90} rot={70} />
      <Sparkle x={18} y={30} s={6} fill="#ffc93c" className="sm-sticker__twinkle" />
    </>
  ),

  // Christmas

  SANTA: (u) => (
    <>
      <defs>
        <Lin id={u('hat')} c={['#ff7a7a', '#d62a3c']} dir="d" />
        <Lin id={u('fur')} c={['#ffffff', '#e6e1ff']} />
      </defs>
      <Blob u={u} />
      <path d="M32 52 C 38 30, 58 18, 80 22 C 94 25, 100 36, 102 46 C 96 42, 90 39, 84 39 C 86 44, 88 48, 88 52 Z" fill={url(u('hat'))} />
      <rect x="28" y="45" width="64" height="12" rx="6" fill={url(u('fur'))} />
      <circle cx="102" cy="48" r="7" fill={url(u('fur'))} />
      <Shine u={u} cx={52} cy={30} rx={8} ry={3} o={0.6} />
      <Face y={72} eyes="happy" mouth="open" />
      <Sparkle x={16} y={30} s={6} fill="#9fd8ff" className="sm-sticker__twinkle" />
      <Sparkle x={106} y={80} s={5} fill="#9fd8ff" className="sm-sticker__twinkle sm-sticker__twinkle--2" />
    </>
  ),

  SNOWMAN: (u) => (
    <>
      <defs>
        <Rad id={u('snow')} c={['#ffffff', '#e6eeff', '#b9c8f0']} />
        <Lin id={u('scarf')} c={['#ff7a7a', '#d62a3c']} />
      </defs>
      <path d="M40 80 L22 66 M26 69 L20 72 M80 80 L98 66 M94 69 L100 72" {...line('#a0673a', 3)} />
      <circle cx="60" cy="86" r="21" fill="#9fb0dc" />
      <circle cx="60" cy="82" r="21" fill={url(u('snow'))} />
      <circle cx="60" cy="50" r="17" fill="#9fb0dc" />
      <circle cx="60" cy="46" r="17" fill={url(u('snow'))} />
      <Shine u={u} cx={52} cy={38} rx={6} ry={3} />
      <Face y={45} s={0.75} eyes="open" mouth="smile" mouthY={10} />
      <polygon points="60,47 72,50 60,53" fill="#ff8a2a" />
      <rect x="40" y="60" width="40" height="8" rx="4" fill={url(u('scarf'))} />
      <rect x="64" y="64" width="9" height="18" rx="3" fill={url(u('scarf'))} transform="rotate(12 68 66)" />
      <circle cx="60" cy="78" r="2.4" fill={INK} />
      <circle cx="60" cy="88" r="2.4" fill={INK} />
      <Sparkle x={18} y={26} s={6} fill="#9fd8ff" className="sm-sticker__twinkle" />
      <Sparkle x={100} y={30} s={5} fill="#9fd8ff" className="sm-sticker__twinkle sm-sticker__twinkle--2" />
      <g className="sm-sticker__snow"><circle cx="30" cy="10" r="2.4" fill="#b9dcff" /><circle cx="92" cy="6" r="2" fill="#b9dcff" /></g>
      <g className="sm-sticker__snow sm-sticker__snow--2"><circle cx="16" cy="4" r="1.8" fill="#b9dcff" /><circle cx="104" cy="14" r="2.4" fill="#b9dcff" /></g>
    </>
  ),

  GIFT: (u) => (
    <>
      <defs>
        <Lin id={u('box')} c={['#ff8fa3', '#e0335a']} dir="d" />
        <Lin id={u('ribbon')} c={['#ffe68a', '#f29a00']} />
        <Rad id={u('body')} c={VIOLET} />
      </defs>
      <ellipse cx="60" cy="58" rx="22" ry="16" fill={url(u('body'))} />
      <Face y={56} s={0.8} eyes="open" mouth="o" blush={false} mouthY={6} />
      <rect x="28" y="62" width="64" height="40" rx="6" fill="#a3122b" transform="translate(0 3)" />
      <rect x="28" y="62" width="64" height="40" rx="6" fill={url(u('box'))} stroke={url(u('rim'))} strokeWidth="1" />
      <rect x="55" y="62" width="10" height="40" fill={url(u('ribbon'))} />
      <ellipse cx="42" cy="62" rx="6" ry="4.5" fill={url(u('body'))} />
      <ellipse cx="78" cy="62" rx="6" ry="4.5" fill={url(u('body'))} />
      <g transform="rotate(-22 30 46)">
        <rect x="22" y="36" width="72" height="12" rx="4" fill={url(u('box'))} stroke={url(u('rim'))} strokeWidth="1" />
        <rect x="53" y="36" width="10" height="12" fill={url(u('ribbon'))} />
        <ellipse cx="50" cy="31" rx="8" ry="5" fill={url(u('ribbon'))} transform="rotate(-20 50 31)" />
        <ellipse cx="66" cy="31" rx="8" ry="5" fill={url(u('ribbon'))} transform="rotate(20 66 31)" />
      </g>
      <Sparkle x={100} y={30} s={7} fill="#ffc93c" className="sm-sticker__twinkle" />
      <Sparkle x={104} y={56} s={4} fill="#ffc93c" className="sm-sticker__twinkle sm-sticker__twinkle--2" />
    </>
  ),

  REINDEER: (u) => (
    <>
      <defs><Rad id={u('nose')} c={['#ffb0b0', '#e0244a', '#a3122b']} /></defs>
      <path d="M46 44 C 42 32, 36 26, 30 18 M38 32 L 26 34 M34 26 L 30 16" {...line('#a0673a', 5)} />
      <path d="M74 44 C 78 32, 84 26, 90 18 M82 32 L 94 34 M86 26 L 90 16" {...line('#a0673a', 5)} />
      <ellipse cx="30" cy="52" rx="5" ry="9" fill="#c98a5a" transform="rotate(-50 30 52)" />
      <ellipse cx="90" cy="52" rx="5" ry="9" fill="#c98a5a" transform="rotate(50 90 52)" />
      <Blob u={u} />
      <Face y={64} eyes="open" mouth="smile" mouthY={17} />
      <circle cx="60" cy="74" r="6" fill={url(u('nose'))} />
      <circle cx="58" cy="72" r="1.8" fill="#fff" opacity=".8" />
      <Sparkle x={104} y={70} s={6} fill="#ff7a7a" className="sm-sticker__twinkle" />
    </>
  ),

  // Easter

  BUNNY: (u) => (
    <>
      <ellipse cx="46" cy="28" rx="8" ry="20" fill={url(u('body'))} transform="rotate(-12 46 28)" />
      <ellipse cx="46" cy="30" rx="3.6" ry="14" fill="#ffadd0" transform="rotate(-12 46 30)" />
      <ellipse cx="76" cy="28" rx="8" ry="20" fill={url(u('body'))} transform="rotate(22 76 28)" />
      <ellipse cx="76" cy="30" rx="3.6" ry="14" fill="#ffadd0" transform="rotate(22 76 30)" />
      <Blob u={u} />
      <Face y={64} eyes="happy" mouth="cat" />
      <Egg u={u} id="egg" x={60} y={88} s={0.9} c={['#fff1f7', '#ffadd0', '#ff7aa8']} />
      <Arm u={u} x={44} y={88} rot={-55} />
      <Arm u={u} x={76} y={88} rot={55} />
      <Sparkle x={100} y={40} s={6} fill="#ffc93c" className="sm-sticker__twinkle" />
    </>
  ),

  HATCHING: (u) => (
    <>
      <defs><Lin id={u('shell')} c={['#fffaf0', '#ffe3b8']} /></defs>
      <g className="sm-sticker__wobble">
        <Blob u={u} dy={-14} />
        <path d="M40 32 L46 38 L52 31 L58 38 L64 31 L70 38 L78 33 C 76 16, 44 16, 40 32 Z" fill={url(u('shell'))} stroke="#f0cf9a" strokeWidth="1" />
        <Face y={54} eyes="open" mouth="o" />
        <path d="M28 66 L36 58 L44 66 L52 57 L60 66 L68 57 L76 66 L84 58 L92 66 C 94 90, 80 104, 60 104 C 40 104, 26 90, 28 66 Z" fill="#e2b98a" transform="translate(0 3)" />
        <path d="M28 66 L36 58 L44 66 L52 57 L60 66 L68 57 L76 66 L84 58 L92 66 C 94 90, 80 104, 60 104 C 40 104, 26 90, 28 66 Z" fill={url(u('shell'))} stroke="#f0cf9a" strokeWidth="1" />
        <circle cx="44" cy="82" r="3" fill="#ffadd0" />
        <circle cx="74" cy="88" r="3.6" fill="#a8e6c9" />
        <circle cx="62" cy="76" r="2.4" fill="#9fd8ff" />
        <Shine u={u} cx={40} cy={74} rx={4} ry={7} rot={20} o={0.6} />
      </g>
      <Sparkle x={100} y={30} s={6} fill="#ffc93c" className="sm-sticker__twinkle" />
      <Sparkle x={18} y={44} s={4} fill="#ffc93c" className="sm-sticker__twinkle sm-sticker__twinkle--2" />
    </>
  ),

  EGG_HUNT: (u) => (
    <>
      <defs><Lin id={u('basket')} c={['#e8b27a', '#b06c3a']} /></defs>
      <path d="M30 72 C 30 34, 90 34, 90 72" {...line('#b06c3a', 6)} />
      <Egg u={u} id="pink" x={40} y={66} s={0.85} c={['#fff1f7', '#ffadd0', '#ff7aa8']} />
      <Egg u={u} id="lemon" x={80} y={66} s={0.85} c={['#fffbe0', '#ffe27a', '#f2b400']} />
      <Egg u={u} id="mint" x={60} y={58} c={['#effff7', '#a8e6c9', '#4cc08a']} />
      <g transform="translate(60 54) scale(.4)">
        <ellipse cx="-10" cy="0" rx="3.8" ry="4.8" fill={INK} />
        <ellipse cx="10" cy="0" rx="3.8" ry="4.8" fill={INK} />
        <path d="M-6 9 Q0 15 6 9" {...line(INK, 2.6)} />
      </g>
      <path d="M22 72 L27 66 L32 72 L37 66 L42 72 L47 66 L52 72 L57 66 L62 72 L67 66 L72 72 L77 66 L82 72 L87 66 L92 72 L98 66 L98 74 L22 74 Z" fill="#7ed957" />
      <path d="M22 72 H98 L90 98 C 88 102, 32 102, 30 98 Z" fill={url(u('basket'))} stroke={url(u('rim'))} strokeWidth="1" />
      <path d="M27 82 H93 M30 92 H90" {...line('#8a5124', 1.6)} opacity=".6" />
      <path d="M42 74 L40 100 M60 74 V101 M78 74 L80 100" {...line('#8a5124', 1.6)} opacity=".6" />
      <Sparkle x={104} y={40} s={6} fill="#ffc93c" className="sm-sticker__twinkle" />
    </>
  ),

  CHOCOLATE: (u) => (
    <>
      <defs>
        <Rad id={u('choc')} c={['#c48552', '#8a5530', '#4a250f']} />
        <Lin id={u('foil')} c={['#fff3b0', '#ffc93c', '#c98200']} dir="d" />
        <mask id={u('bite')} maskUnits="userSpaceOnUse" x="0" y="0" width="120" height="120">
          <rect width="120" height="120" fill="#fff" />
          <circle cx="74" cy="25" r="8" fill="#000" />
          <circle cx="82" cy="35" r="7" fill="#000" />
        </mask>
        <clipPath id={u('shape')}>
          <path d={EGG} transform="translate(60 62) scale(2 2.4)" />
        </clipPath>
      </defs>
      <g mask={url(u('bite'))}>
        <path d={EGG} transform="translate(60 66) scale(2 2.4)" fill="#3a1c08" />
        <path d={EGG} transform="translate(60 62) scale(2 2.4)" fill={url(u('choc'))} />
        <g clipPath={url(u('shape'))}>
          <path d="M20 74 l6 -5 l6 5 l6 -5 l6 5 l6 -5 l6 5 l6 -5 l6 5 l6 -5 l6 5 l6 -5 l6 5 l6 -5 V110 H20 Z" fill={url(u('foil'))} />
          <path d="M34 86 l8 10 M58 84 l6 12 M80 86 l-6 10" {...line('#fff', 1.4)} opacity=".6" />
        </g>
      </g>
      <Shine u={u} cx={44} cy={36} rx={6} ry={10} rot={25} o={0.45} />
      <Face y={56} eyes="closed" mouth="cat" ink="#ffe9c9" />
      <circle cx="90" cy="30" r="1.8" fill="#8a5530" />
      <circle cx="96" cy="38" r="1.2" fill="#8a5530" />
      <Sparkle x={18} y={34} s={6} fill="#ffc93c" className="sm-sticker__twinkle" />
    </>
  ),

  // Halloween

  GHOST: (u) => (
    <>
      <defs><Rad id={u('sheet')} c={['#ffffff', '#eeeaff', '#c9c2ff']} /></defs>
      <g className="sm-sticker__bob">
        <ellipse cx="25" cy="62" rx="6" ry="10" fill={url(u('sheet'))} transform="rotate(-55 25 62)" />
        <ellipse cx="95" cy="62" rx="6" ry="10" fill={url(u('sheet'))} transform="rotate(55 95 62)" />
        <path d="M30 96 V58 C 30 36, 44 24, 60 24 C 76 24, 90 36, 90 58 V96 Q 85 88, 80 96 Q 75 104, 70 96 Q 65 88, 60 96 Q 55 104, 50 96 Q 45 88, 40 96 Q 35 104, 30 96 Z"
          fill={url(u('sheet'))} stroke={url(u('rim'))} strokeWidth="1.2" />
        <Shine u={u} cx={44} cy={38} rx={8} ry={4} o={0.9} />
        <Face y={58} eyes="open" mouth="o" mouthY={9} />
      </g>
      <Sparkle x={100} y={28} s={6} fill="#b8b0ff" className="sm-sticker__twinkle" />
      <Sparkle x={16} y={36} s={4} fill="#b8b0ff" className="sm-sticker__twinkle sm-sticker__twinkle--2" />
    </>
  ),

  PUMPKIN: (u) => (
    <>
      <defs>
        <Rad id={u('pumpkin')} c={['#ffd09a', '#ff8a2a', '#d9580f']} />
        <Rad id={u('glow')} c={['#fff8c4', '#ffd23f', '#ff9f1a']} cx={0.5} cy={0.5} />
      </defs>
      <path d="M57 46 C 56 38, 60 32, 67 30 L 69 34 C 65 36, 63 41, 64 47 Z" fill="#5c8a2e" />
      <path d="M66 36 C 74 30, 82 32, 86 38 C 78 38, 72 38, 66 36 Z" fill="#7ed957" />
      <g transform="translate(0 4)" fill="#a8400a">
        <ellipse cx="40" cy="74" rx="18" ry="24" />
        <ellipse cx="80" cy="74" rx="18" ry="24" />
        <ellipse cx="60" cy="74" rx="22" ry="26" />
      </g>
      <ellipse cx="40" cy="74" rx="18" ry="24" fill={url(u('pumpkin'))} />
      <ellipse cx="80" cy="74" rx="18" ry="24" fill={url(u('pumpkin'))} />
      <ellipse cx="60" cy="74" rx="22" ry="26" fill={url(u('pumpkin'))} stroke={url(u('rim'))} strokeWidth="1" />
      <g className="sm-sticker__glow">
        <path d="M44 68 L50 58 L56 68 Z M64 68 L70 58 L76 68 Z" fill={url(u('glow'))} />
        <path d="M42 78 Q60 96 78 78 L73 80 L69 76 L65 81 L60 77 L55 81 L51 76 L47 80 Z" fill={url(u('glow'))} />
      </g>
      <Shine u={u} cx={36} cy={60} rx={4} ry={8} rot={20} o={0.6} />
      <Sparkle x={100} y={34} s={6} fill="#ffc93c" className="sm-sticker__twinkle" />
    </>
  ),

  WITCH: (u) => (
    <>
      <defs><Lin id={u('hat')} c={['#5a4bb0', '#1e1748']} dir="d" /></defs>
      <path d="M92 86 L106 60" {...line('#a0673a', 3)} />
      <Blob u={u} />
      <ellipse cx="60" cy="42" rx="38" ry="8" fill="#1e1748" />
      <path d="M41 42 C 46 30, 50 14, 68 6 C 65 15, 70 24, 79 42 Z" fill={url(u('hat'))} stroke={url(u('rim'))} strokeWidth="1" />
      <path d="M43 36 L77 36 L79 42 L41 42 Z" fill="#ff8fc0" />
      <rect x="56" y="35" width="8" height="8" rx="1.5" fill="none" stroke="#ffc93c" strokeWidth="2" />
      <Face y={70} eyes="wink" mouth="grin" />
      <Arm u={u} x={92} y={84} rot={-30} />
      <Sparkle x={107} y={57} s={9} fill="#ffc93c" className="sm-sticker__twinkle" />
      <Sparkle x={16} y={30} s={5} fill="#b8b0ff" className="sm-sticker__twinkle sm-sticker__twinkle--2" />
    </>
  ),

  VAMPIRE: (u) => (
    <>
      <defs><Lin id={u('cape')} c={['#ff5a6e', '#a3122b']} dir="d" /></defs>
      <path d="M22 66 C 20 90, 30 104, 60 104 C 90 104, 100 90, 98 66 Z" fill="#2b1640" />
      <path d="M18 72 L 28 32 L 46 58 Z M102 72 L 92 32 L 74 58 Z" fill={url(u('cape'))} />
      <Blob u={u} c={['#ffffff', '#e4dcff', '#a89cf0']} under="#7f72d6" />
      <path d="M36 50 C 42 36, 54 34, 60 46 C 66 34, 78 36, 84 50 C 76 42, 66 42, 60 52 C 54 42, 44 42, 36 50 Z" fill="#2b1640" />
      <Face y={70} eyes="open" mouth="fangs" />
      <g className="sm-sticker__bob">
        <path d="M0 0 C -4 -6, -10 -6, -14 -2 C -10 -2, -8 0, -8 3 C -6 1, -3 1, 0 4 C 3 1, 6 1, 8 3 C 8 0, 10 -2, 14 -2 C 10 -6, 4 -6, 0 0 Z"
          transform="translate(98 22)" fill="#2b1640" />
      </g>
    </>
  ),

  // summer, and the Santos Populares

  SUNGLASSES: (u) => (
    <>
      <defs>
        <Rad id={u('sun')} c={['#fff8c4', '#ffd23f', '#ff9f1a']} />
        <Lin id={u('lens')} c={['#4b3d99', '#120d2e']} dir="d" />
      </defs>
      <g className="sm-sticker__spin">
        <path d="M92 6 V12 M92 48 V54 M68 30 H74 M110 30 H116 M75 13 L79 17 M105 43 L109 47 M75 47 L79 43 M105 17 L109 13" {...line('#ffc93c', 3)} />
      </g>
      <circle cx="92" cy="30" r="13" fill={url(u('sun'))} />
      <Blob u={u} />
      <Face y={70} eyes="none" mouth="smirk" />
      <rect x="35" y="58" width="21" height="13" rx="5" fill={url(u('lens'))} />
      <rect x="64" y="58" width="21" height="13" rx="5" fill={url(u('lens'))} />
      <path d="M56 62 Q60 59 64 62 M35 62 L28 59 M85 62 L92 59" {...line('#120d2e', 2.2)} />
      <path d="M39 68 L45 60 M68 68 L74 60" {...line('#ff8fc0', 2)} opacity=".8" />
      <Sparkle x={52} y={60} s={3.5} fill="#fff" />
    </>
  ),

  ICE_CREAM: (u) => (
    <>
      <defs>
        <Lin id={u('cone')} c={['#f6c47e', '#c98a3c']} />
        <Rad id={u('mint')} c={['#effff7', '#a8e6c9', '#4cc08a']} />
        <Rad id={u('berry')} c={['#fff1f7', '#ffadd0', '#ff6f9f']} />
        <Rad id={u('vanilla')} c={['#ffffff', '#fff3d6', '#f0cf9a']} />
        <clipPath id={u('waffle')}><path d="M42 70 L60 106 L78 70 Z" /></clipPath>
      </defs>
      <path d="M42 70 L60 106 L78 70 Z" fill={url(u('cone'))} />
      <g clipPath={url(u('waffle'))}>
        <path d="M40 76 L70 106 M40 88 L60 108 M52 70 L80 98 M64 70 L84 90 M80 76 L50 106 M80 88 L60 108 M68 70 L40 98 M56 70 L36 90" {...line('#a86b28', 1.4)} />
      </g>
      <circle cx="47" cy="64" r="13" fill={url(u('mint'))} />
      <circle cx="73" cy="64" r="13" fill={url(u('berry'))} />
      <path d="M36 70 Q 40 78, 44 72 Q 48 80, 52 72" fill="#a8e6c9" />
      <circle cx="60" cy="44" r="17" fill={url(u('vanilla'))} stroke={url(u('rim'))} strokeWidth="1" />
      <Shine u={u} cx={52} cy={36} rx={5} ry={3} />
      <Face y={46} s={0.7} eyes="happy" mouth="open" />
      <rect x="48" y="30" width="5" height="2" rx="1" fill="#ff7aa8" transform="rotate(30 50 31)" />
      <rect x="66" y="32" width="5" height="2" rx="1" fill="#7fbfff" transform="rotate(-20 68 33)" />
      <rect x="58" y="28" width="5" height="2" rx="1" fill="#5fd3a0" transform="rotate(70 60 29)" />
      <path d="M66 24 C 66 18, 70 14, 74 12" {...line('#5c8a2e', 1.8)} />
      <circle cx="65" cy="25" r="5" fill="#e0244a" />
      <circle cx="63.5" cy="23.5" r="1.4" fill="#fff" opacity=".8" />
      <Sparkle x={98} y={40} s={6} fill="#ffc93c" className="sm-sticker__twinkle" />
    </>
  ),

  POOL_FLOAT: (u) => (
    <>
      <defs>
        <Lin id={u('water')} c={['#bfe6ff', '#4aa8ff']} />
        <Lin id={u('ring')} c={['#ffadd0', '#ff5a8a']} />
        <clipPath id={u('front')}><path d="M18 82 A 42 17 0 0 0 102 82 L 82 82 A 22 6 0 0 1 38 82 Z" /></clipPath>
      </defs>
      <ellipse cx="60" cy="98" rx="48" ry="11" fill={url(u('water'))} />
      <path d="M22 98 q6 -3 12 0 t12 0 M74 98 q6 -3 12 0 t12 0" {...line('#fff', 1.8)} opacity=".8" />
      <path d="M18 82 A 42 17 0 0 1 102 82 L 82 82 A 22 6 0 0 0 38 82 Z" fill="#d63c6b" />
      <g className="sm-sticker__bob">
      <Blob u={u} dy={-16} />
      <Face y={52} eyes="happy" mouth="open" />
      <path d="M18 82 A 42 17 0 0 0 102 82 L 82 82 A 22 6 0 0 1 38 82 Z" fill={url(u('ring'))} stroke={url(u('rim'))} strokeWidth="1" />
      <g clipPath={url(u('front'))} fill="#fff">
        <rect x="26" y="78" width="11" height="26" />
        <rect x="55" y="78" width="10" height="26" />
        <rect x="83" y="78" width="11" height="26" />
      </g>
      <Arm u={u} x={32} y={74} rot={60} />
      <Arm u={u} x={88} y={74} rot={-60} />
      </g>
      <Sparkle x={102} y={26} s={7} fill="#ffc93c" className="sm-sticker__twinkle" />
    </>
  ),

  SARDINE: (u) => (
    <>
      <defs>
        <Lin id={u('bread')} c={['#f3c98b', '#c98a3c']} />
        <Lin id={u('fish')} c={['#eef7ff', '#9dbde0', '#4a6fa5']} />
      </defs>
      <path d="M8 18 Q 60 50 112 18" {...line('#a0673a', 1.2)} />
      {[[20, '#ff8fc0'], [38, '#ffe27a'], [56, '#5fd3a0'], [74, '#7fbfff'], [92, '#ffb27a']].map(([x, colour]) => {
        const t = ((x as number) - 8) / 104;
        const y = 18 + 64 * t * (1 - t);
        return <polygon key={x} points={`${(x as number) - 6},${y} ${(x as number) + 6},${y} ${x},${y + 11}`} fill={colour as string} />;
      })}
      <path d="M18 88 C 16 76, 28 72, 38 74 L 84 74 C 96 72, 106 80, 102 90 C 100 98, 92 100, 60 100 C 32 100, 20 98, 18 88 Z" fill={url(u('bread'))} stroke={url(u('rim'))} strokeWidth="1" />
      <path d="M26 86 C 26 80, 34 79, 40 80 L 82 80 C 92 79, 96 84, 94 89 C 92 94, 80 94, 60 94 C 38 94, 27 92, 26 86 Z" fill="#fff0d4" />
      <path d="M22 68 C 34 52, 72 50, 90 62 L 106 52 C 103 62, 103 68, 106 78 L 90 70 C 72 82, 34 82, 22 68 Z" fill={url(u('fish'))} stroke={url(u('rim'))} strokeWidth="1" />
      <path d="M50 58 L44 72 M62 58 L56 74 M74 60 L68 74" {...line('#3a2a20', 2.2)} opacity=".45" />
      <circle cx="34" cy="64" r="4" fill="#fff" />
      <circle cx="34.6" cy="64.6" r="2.2" fill={INK} />
      <ellipse cx="38" cy="71" rx="3.4" ry="1.8" fill="#ff8fc0" opacity=".7" />
      <path d="M26 70 Q29 73 32 71" {...line(INK, 1.6)} />
      <Shine u={u} cx={48} cy={57} rx={8} ry={2} rot={-8} o={0.7} />
    </>
  ),
};

export interface StickerArtProps {
  id: StickerId;
  size?: number;
  // accessible name, the sticker's name by default; "" when it's only decoration
  title?: string;
  // keeps moving, for the animated stickers in the chat (respects reduced motion)
  animated?: boolean;
  className?: string;
}

export function StickerArt({ id, size = 120, title, animated, className }: StickerArtProps) {
  const { t } = useTranslation();
  const uid = useSafeId('sticker');
  const u: U = (n) => `${uid}-${n}`;
  const name = title ?? t(`stickers.${id}`);
  return (
    <ClaySvg u={u} size={size} name={name} className={cx('sm-sticker', animated && 'sm-sticker--loop', className)} objectClassName="sm-sticker__object" shadowClassName="sm-sticker__shadow">
      {ART[id](u)}
    </ClaySvg>
  );
}
