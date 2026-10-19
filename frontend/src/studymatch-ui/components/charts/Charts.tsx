import { motion } from 'framer-motion';
import { useState,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import { EASE_OUT } from '../../utils/shared';
import { type ProfileKey } from '../badge/Badge';
import { PROFILES,PROFILE_KEYS } from '../badge/Badge.shared';
import './charts.css';

export interface BarDatum {
  label: string;
  value: number;
  color?: string;
}

export interface BarChartProps {
  data: BarDatum[];
  max?: number;
  height?: number;
  format?: (value: number) => string;
  highlight?: string;
  reference?: { value: number; label: string };
  label: string;
  className?: string;
}

export function BarChart({ data, max, height = 220, format = (v) => String(v), highlight, reference, label, className }: BarChartProps) {
  useTranslation();
  const top = max ?? Math.max(1, ...data.map((d) => d.value), reference?.value ?? 0);
  const [hover, setHover] = useState<string | null>(null);
  return (
    <figure className={cx('sm-chart', className)} aria-label={label}>
      <div className="sm-bars" style={{ height }} aria-hidden>
        {reference && (
          <div className="sm-bars__ref" style={{ bottom: `${(reference.value / top) * 100}%` }}>
            <span>{reference.label}</span>
          </div>
        )}
        {data.map((d, i) => {
          const dim = (highlight && highlight !== d.label) || (hover && hover !== d.label);
          return (
            <div key={d.label} className="sm-bars__col" onMouseEnter={() => setHover(d.label)} onMouseLeave={() => setHover(null)}>
              <span className="sm-bars__value">{format(d.value)}</span>
              <motion.i
                className="sm-bars__bar"
                style={{ background: d.color ?? 'var(--sm-grad-brand)', opacity: dim ? 0.35 : 1 }}
                initial={{ height: 0 }}
                whileInView={{ height: `${(d.value / top) * 100}%` }}
                viewport={{ once: true }}
                transition={{ duration: 0.7, delay: i * 0.04, ease: EASE_OUT }}
              />
            </div>
          );
        })}
      </div>
      <div className="sm-bars__labels" aria-hidden>
        {data.map((d) => (
          <span key={d.label}>{d.label}</span>
        ))}
      </div>
      <table className="sm-visually-hidden">
        <caption>{label}</caption>
        <tbody>
          {data.map((d) => (
            <tr key={d.label}>
              <th scope="row">{d.label}</th>
              <td>{format(d.value)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

export interface DonutSegment {
  label: string;
  value: number;
  color: string;
}

export interface DonutChartProps {
  segments: DonutSegment[];
  size?: number;
  thickness?: number;
  center?: ReactNode;
  centerCaption?: ReactNode;
  legend?: boolean;
  label: string;
  className?: string;
}

export function DonutChart({ segments, size = 168, thickness = 22, center, centerCaption, legend = true, label, className }: DonutChartProps) {
  useTranslation();
  const total = segments.reduce((s, x) => s + x.value, 0) || 1;
  const r = (size - thickness) / 2;
  const c = 2 * Math.PI * r;
  return (
    <figure className={cx('sm-chart', 'sm-donut', className)} aria-label={label}>
      <span className="sm-donut__ring" style={{ width: size, height: size }}>
        <svg width={size} height={size} aria-hidden style={{ transform: 'rotate(-90deg)' }}>
          <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--sm-fill)" strokeWidth={thickness} />
          {segments.map((s, i) => {
            const len = (s.value / total) * c;
            const gap = segments.length > 1 ? Math.min(4, len / 3) : 0;
            const offset = -segments.slice(0, i).reduce((sum, segment) => sum + segment.value / total * c, 0);
            return (
              <motion.circle
                key={s.label}
                cx={size / 2}
                cy={size / 2}
                r={r}
                fill="none"
                stroke={s.color}
                strokeWidth={thickness}
                strokeDashoffset={offset}
                initial={{ strokeDasharray: `0 ${c}` }}
                animate={{ strokeDasharray: `${Math.max(0, len - gap)} ${c}` }}
                transition={{ duration: 0.6, delay: i * 0.08, ease: EASE_OUT }}
              />
            );
          })}
        </svg>
        {(center || centerCaption) && (
          <span className="sm-donut__center">
            {center && <b>{center}</b>}
            {centerCaption && <small>{centerCaption}</small>}
          </span>
        )}
      </span>
      {legend && (
        <ul className="sm-legend">
          {segments.map((s) => (
            <li key={s.label}>
              <i style={{ background: s.color }} aria-hidden />
              <span>{s.label}</span>
              <b>{Math.round((s.value / total) * 100)}%</b>
            </li>
          ))}
        </ul>
      )}
    </figure>
  );
}

export interface ProfileBarsProps {
  values: Record<ProfileKey, number>;
  emphasizeTop?: boolean;
  className?: string;
}

export function ProfileBars({ values, emphasizeTop = true, className }: ProfileBarsProps) {
  useTranslation();
  const sorted = [...PROFILE_KEYS].sort((a, b) => values[b] - values[a]);
  return (
    <ul className={cx('sm-profile-bars', className)} aria-label={i18n.t('ui.personality_profile')}>
      {sorted.map((k, i) => {
        const p = PROFILES[k];
        const Icon = p.icon;
        return (
          <li key={k} data-top={emphasizeTop && i === 0}>
            <span className="sm-profile-bars__icon" style={{ background: p.color }} aria-hidden>
              <Icon size={14} strokeWidth={2.5} />
            </span>
            <span className="sm-profile-bars__label">{p.label}</span>
            <span className="sm-profile-bars__track" aria-hidden>
              <motion.i style={{ background: p.color }} initial={{ width: 0 }} animate={{ width: `${values[k]}%` }} transition={{ duration: 0.8, delay: i * 0.08, ease: EASE_OUT }} />
            </span>
            <b className="sm-num">{Math.round(values[k])}%</b>
          </li>
        );
      })}
    </ul>
  );
}
