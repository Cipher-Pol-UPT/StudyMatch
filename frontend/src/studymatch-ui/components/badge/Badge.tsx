import { Compass, Cpu, Lightbulb, Target } from 'lucide-react';
import type { HTMLAttributes } from 'react';
import { cx } from '../../utils/cx';
import type { IconComponent } from '../../utils/shared';
import './badge.css';

// ─── Badge ─────────────────────────────────────────────────
export type BadgeTone = 'violet' | 'spark' | 'black' | 'danger' | 'neutral';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
  /** Render a small dot without content (e.g. "unread"). */
  dot?: boolean;
}

/** Counters and tiny labels. Spark is for celebrating something new. */
export function Badge({ tone = 'violet', dot, className, children, ...rest }: BadgeProps) {
  return (
    <span className={cx('sm-badge', tone !== 'violet' && `sm-badge--${tone}`, dot && 'sm-badge--dot', className)} {...rest}>
      {dot ? null : children}
    </span>
  );
}

// ─── Status pill ───────────────────────────────────────────
export type StatusTone = 'neutral' | 'success' | 'warning' | 'danger' | 'violet' | 'info';

export interface StatusPillProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: StatusTone;
  size?: 'sm' | 'md';
  /** Animated dot for live states ("In progress", "Live now"). */
  pulse?: boolean;
}

/** A coloured dot and a word. Colour always comes with text, never alone. */
export function StatusPill({ tone = 'neutral', size = 'md', pulse, className, children, ...rest }: StatusPillProps) {
  return (
    <span className={cx('sm-status', `sm-status--${tone}`, size === 'sm' && 'sm-status--sm', pulse && 'sm-status--pulse', className)} {...rest}>
      <i aria-hidden />
      {children}
    </span>
  );
}

/**
 * Ready-made mapping for StudyMatch domain states, so every screen
 * shows the same colour for the same state.
 */
export const DOMAIN_STATUS: Record<string, { tone: StatusTone; label: string }> = {
  // Groups
  draft: { tone: 'warning', label: 'Draft' },
  published: { tone: 'success', label: 'Published' },
  // Enrolments
  'in-progress': { tone: 'violet', label: 'In progress' },
  passed: { tone: 'success', label: 'Passed' },
  failed: { tone: 'danger', label: 'Failed' },
  // StudyRooms
  scheduled: { tone: 'info', label: 'Scheduled' },
  full: { tone: 'warning', label: 'Full' },
  completed: { tone: 'neutral', label: 'Completed' },
  cancelled: { tone: 'danger', label: 'Cancelled' },
  // Accounts
  active: { tone: 'success', label: 'Active' },
  pending: { tone: 'warning', label: 'Pending activation' },
  blocked: { tone: 'danger', label: 'Blocked' },
  // Tutoring applications
  approved: { tone: 'success', label: 'Approved' },
  rejected: { tone: 'danger', label: 'Rejected' },
  joined: { tone: 'violet', label: 'Joined' },
  'under-review': { tone: 'warning', label: 'Under review' },
};

export function DomainStatus({ status, size }: { status: keyof typeof DOMAIN_STATUS | string; size?: 'sm' | 'md' }) {
  const s = DOMAIN_STATUS[status] ?? { tone: 'neutral' as const, label: status };
  return (
    <StatusPill tone={s.tone} size={size}>
      {s.label}
    </StatusPill>
  );
}

// ─── Profiles ──────────────────────────────────────────────
export type ProfileKey = 'leadership' | 'technical' | 'creativity' | 'execution';

/** The four team-role profiles of the personality test. Colour + icon + label always travel together. */
export const PROFILES: Record<ProfileKey, { label: string; short: string; color: string; icon: IconComponent; description: string }> = {
  leadership: { label: 'Leadership', short: 'Lead', color: 'var(--sm-lead)', icon: Compass, description: 'Sets direction, organises the team and keeps everyone aligned.' },
  technical: { label: 'Problem Solving', short: 'Tech', color: 'var(--sm-tech)', icon: Cpu, description: 'Breaks problems down and finds the technical path forward.' },
  creativity: { label: 'Creativity', short: 'Creative', color: 'var(--sm-creative)', icon: Lightbulb, description: 'Brings new ideas, alternatives and a fresh point of view.' },
  execution: { label: 'Execution', short: 'Exec', color: 'var(--sm-exec)', icon: Target, description: 'Turns plans into finished work, on time and in detail.' },
};

export const PROFILE_KEYS = Object.keys(PROFILES) as ProfileKey[];

export interface ProfileTagProps {
  profile: ProfileKey;
  /** Use the short label ("Lead", "Tech"…). */
  short?: boolean;
  size?: 'sm' | 'md';
  className?: string;
}

export function ProfileTag({ profile, short, size = 'md', className }: ProfileTagProps) {
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
