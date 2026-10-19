import { motion } from 'framer-motion';
import type { CSSProperties,ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import { EASE_OUT } from '../../utils/shared';
import './progress.css';

export interface SpinnerProps {
  size?: number;
  onDark?: boolean;
  label?: string;
}

export function Spinner({ size = 28, onDark, label = i18n.t('ui.loading') }: SpinnerProps) {
  useTranslation();
  return <span className={cx('sm-spinner', onDark && 'sm-spinner--on-dark')} style={{ width: size, height: size, borderWidth: Math.max(2, size / 9) }} role="status" aria-label={label} />;
}

export interface ProgressBarProps {
  value?: number;
  max?: number;
  indeterminate?: boolean;
  label: string;
  showLabel?: boolean;
  valueText?: ReactNode;
  tone?: 'brand' | 'success' | 'warning' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  color?: string;
  className?: string;
}

export function ProgressBar({ value = 0, max = 100, indeterminate, label, showLabel, valueText, tone = 'brand', size = 'md', color, className }: ProgressBarProps) {
  useTranslation();
  const pct = Math.max(0, Math.min(100, (value / max) * 100));
  const bar = (
    <div
      className={cx('sm-progress', size !== 'md' && `sm-progress--${size}`, tone !== 'brand' && `sm-progress--${tone}`, indeterminate && 'sm-progress--indeterminate', !showLabel && className)}
      role="progressbar"
      aria-label={label}
      aria-valuenow={indeterminate ? undefined : value}
      aria-valuemin={0}
      aria-valuemax={max}
    >
      <i style={{ width: `${pct}%`, background: color }} />
    </div>
  );
  if (!showLabel) return bar;
  return (
    <div className={cx('sm-progress-wrap', className)}>
      <div className="sm-progress-head">
        <span>{label}</span>
        <span>{valueText ?? (indeterminate ? '' : `${Math.round(pct)}%`)}</span>
      </div>
      {bar}
    </div>
  );
}

export interface ScoreRingProps {
  value: number;
  size?: number;
  stroke?: number;
  color?: string;
  track?: string;
  label?: string;
  children?: ReactNode;
}

export function ScoreRing({ value, size = 72, stroke = 7, color = 'var(--sm-violet)', track = 'var(--sm-fill)', label = i18n.t('ui.score'), children }: ScoreRingProps) {
  useTranslation();
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const v = Math.max(0, Math.min(100, value));
  return (
    <span className="sm-ring" style={{ width: size, height: size }} role="img" aria-label={i18n.t('ui.score_label', {label,value:Math.round(v)})}>
      <svg width={size} height={size} aria-hidden>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={track} strokeWidth={stroke} />
        <motion.circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke={color}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={c}
          initial={{ strokeDashoffset: c }}
          animate={{ strokeDashoffset: c * (1 - v / 100) }}
          transition={{ duration: 0.9, ease: EASE_OUT }}
        />
      </svg>
      <span className="sm-ring__value" style={{ fontSize: size * 0.3 }} aria-hidden>
        {children ?? Math.round(v)}
      </span>
    </span>
  );
}

export interface SkeletonProps {
  w?: CSSProperties['width'];
  h?: CSSProperties['height'];
  r?: CSSProperties['borderRadius'];
  className?: string;
}

export function Skeleton({ w = '100%', h = 16, r, className }: SkeletonProps) {
  useTranslation();
  return <span className={cx('sm-skel', className)} style={{ width: w, height: h, borderRadius: r }} aria-hidden />;
}
