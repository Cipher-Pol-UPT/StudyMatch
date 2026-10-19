import type { HTMLAttributes } from 'react';
import { useTranslation } from 'react-i18next';
import { cx } from '../../utils/cx';
import './badge.css';
import { DOMAIN_STATUS,PROFILES } from './Badge.shared';

export type BadgeTone = 'violet' | 'spark' | 'black' | 'danger' | 'neutral';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
  dot?: boolean;
}

export function Badge({ tone = 'violet', dot, className, children, ...rest }: BadgeProps) {
  useTranslation();
  return (
    <span className={cx('sm-badge', tone !== 'violet' && `sm-badge--${tone}`, dot && 'sm-badge--dot', className)} {...rest}>
      {dot ? null : children}
    </span>
  );
}

export type StatusTone = 'neutral' | 'success' | 'warning' | 'danger' | 'violet' | 'info';

export interface StatusPillProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: StatusTone;
  size?: 'sm' | 'md';
  pulse?: boolean;
}

export function StatusPill({ tone = 'neutral', size = 'md', pulse, className, children, ...rest }: StatusPillProps) {
  useTranslation();
  return (
    <span className={cx('sm-status', `sm-status--${tone}`, size === 'sm' && 'sm-status--sm', pulse && 'sm-status--pulse', className)} {...rest}>
      <i aria-hidden />
      {children}
    </span>
  );
}

export function DomainStatus({ status, size }: { status: keyof typeof DOMAIN_STATUS | string; size?: 'sm' | 'md' }) {
  useTranslation();
  const s = DOMAIN_STATUS[status] ?? { tone: 'neutral' as const, label: status };
  return (
    <StatusPill tone={s.tone} size={size}>
      {s.label}
    </StatusPill>
  );
}

export type ProfileKey = 'leadership' | 'technical' | 'creativity' | 'execution';

export interface ProfileTagProps {
  profile: ProfileKey;
  short?: boolean;
  size?: 'sm' | 'md';
  className?: string;
}

export function ProfileTag({ profile, short, size = 'md', className }: ProfileTagProps) {
  useTranslation();
  const p = PROFILES[profile];
  const Icon = p.icon;
  return (
    <span
      className={cx('sm-status', 'sm-profile-tag', size === 'sm' && 'sm-status--sm', className)}
      style={{ background: `color-mix(in srgb, ${p.color} 12%, white)` }}
    >
      <span className="sm-profile-tag__icon" style={{ background: p.color }}>
        <Icon size={11} strokeWidth={2.5} aria-hidden />
      </span>
      {short ? p.short : p.label}
    </span>
  );
}

export function ProfileDot({ profile, size = 26, ring = '#fff' }: { profile: ProfileKey; size?: number; ring?: string }) {
  useTranslation();
  const p = PROFILES[profile];
  const Icon = p.icon;
  return (
    <span
      title={p.label}
      aria-label={p.label}
      role="img"
      style={{ width: size, height: size, borderRadius: '50%', background: p.color, color: '#fff', display: 'inline-grid', placeItems: 'center', boxShadow: `0 0 0 2px ${ring}`, flexShrink: 0 }}
    >
      <Icon size={size * 0.5} strokeWidth={2.5} aria-hidden />
    </span>
  );
}
