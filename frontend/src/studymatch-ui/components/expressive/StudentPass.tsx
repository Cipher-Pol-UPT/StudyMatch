import { motion } from 'framer-motion';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { hashString } from '../../utils/shared';
import { type ProfileKey } from '../badge/Badge';
import { PROFILES } from '../badge/Badge.shared';
import './expressive.css';

function Pattern({ seed, color }: { seed: string; color: string }) {
  useTranslation();
  let h = hashString(seed) || 7;
  const rows = 6;
  const cols = 10;
  return (
    <svg className="sm-pass__pattern" viewBox="0 0 100 63" preserveAspectRatio="none" aria-hidden>
      <rect width="100" height="63" fill={color} />
      {Array.from({ length: rows * cols }).map((_, i) => {
        h = (h * 1103515245 + 12345) >>> 0;
        const x = (i % cols) * 10 + 5;
        const y = Math.floor(i / cols) * 10.5 + 5;
        const k = h % 5;
        return k === 0 ? <circle key={i} cx={x} cy={y} r={3.2} fill="rgba(255,255,255,.14)" /> : k === 1 ? <rect key={i} x={x - 3} y={y - 3} width={6} height={6} rx={2} fill="rgba(0,0,0,.12)" /> : null;
      })}
    </svg>
  );
}

export interface StudentPassProps {
  name: string;
  number: string;
  year: number;
  group: string;
  profile: ProfileKey;
  teammates?: string[];
  color?: string;
  programme?: string;
}

export function StudentPass({ name, number, year, group, profile, teammates = [], color = 'var(--sm-violet)', programme = 'StudyMatch' }: StudentPassProps) {
  useTranslation();
  const [flipped, setFlipped] = useState(false);
  const [tilt, setTilt] = useState({ x: 0, y: 0, gx: 50, gy: 50 });
  const P = PROFILES[profile];
  const Icon = P.icon;
  return (
    <div className="sm-pass-scene">
      <motion.button
        type="button"
        className="sm-pass-btn"
        onClick={() => setFlipped((f) => !f)}
        onPointerMove={(e) => {
          if (e.pointerType !== 'mouse') return;
          const r = e.currentTarget.getBoundingClientRect();
          const px = (e.clientX - r.left) / r.width;
          const py = (e.clientY - r.top) / r.height;
          setTilt({ x: (py - 0.5) * -10, y: (px - 0.5) * 12, gx: px * 100, gy: py * 100 });
        }}
        onPointerLeave={() => setTilt({ x: 0, y: 0, gx: 50, gy: 50 })}
        animate={{ rotateY: (flipped ? 180 : 0) + tilt.y, rotateX: tilt.x }}
        transition={{ type: 'spring', stiffness: 260, damping: 22 }}
        aria-pressed={flipped}
        aria-label={i18n.t('ui.student_pass', {name,side: flipped ? i18n.t('ui.showing_teammates') : i18n.t('ui.showing_front')})}
      >
        <div className="sm-pass">
          <Pattern seed={name} color={color} />
          <span className="sm-pass__sheen" style={{ background: `radial-gradient(60% 60% at ${tilt.gx}% ${tilt.gy}%, rgba(255,255,255,.55), transparent 70%)` }} />
          <div className="sm-pass__row">
            <span style={{ fontSize: 15, fontWeight: 500, letterSpacing: '-0.01em' }}>{programme}</span>
            <span className="sm-pass__year"> {i18n.t('ui.year')} {year}</span>
          </div>
          <div className="sm-pass__chip" />
          <div className="sm-pass__row" style={{ alignItems: 'flex-end' }}>
            <div>
              <div className="sm-pass__name">{name}</div>
              <div className="sm-pass__meta">
                {number} · {group}
              </div>
            </div>
            <span style={{ width: 36, height: 36, borderRadius: '50%', background: '#fff', color: P.color, display: 'grid', placeItems: 'center', flexShrink: 0 }} title={P.label}>
              <Icon size={18} strokeWidth={2.5} aria-hidden />
            </span>
          </div>
        </div>
        <div className="sm-pass sm-pass--back">
          <div className="sm-pass__row">
            <span className="sm-eyebrow" style={{ color: 'rgba(255,255,255,.6)' }}>
              {group} {i18n.t('ui.teammates')}
            </span>
            <span className="sm-eyebrow" style={{ color: 'var(--sm-spark)' }}>
              {P.short}
            </span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {(teammates.length ? teammates : [i18n.t('ui.no_group_yet')]).slice(0, 4).map((t) => (
              <div key={t} style={{ fontSize: 17, letterSpacing: '-0.01em' }}>
                {t}
              </div>
            ))}
          </div>
          <div style={{ fontFamily: 'var(--sm-mono)', fontSize: 11, opacity: 0.5 }}> {i18n.t('ui.tap_to_flip_back')} </div>
        </div>
      </motion.button>
    </div>
  );
}
