import type { ReactNode } from 'react';
import { cx } from '../../utils/cx';
import type { IconComponent } from '../../utils/shared';
import './empty.css';

export interface EmptyStateProps {
  icon: IconComponent;
  title: ReactNode;
  body?: ReactNode;
  action?: ReactNode;
  tone?: 'lavender' | 'sky' | 'peach' | 'mint' | 'lemon' | 'rose';
  compact?: boolean;
  className?: string;
}

const TONE: Record<NonNullable<EmptyStateProps['tone']>, [string, string]> = {
  lavender: ['var(--sm-tint-lavender)', 'var(--sm-violet)'],
  sky: ['var(--sm-tint-sky)', '#1f86c0'],
  peach: ['var(--sm-tint-peach)', '#d4561f'],
  mint: ['var(--sm-tint-mint)', '#13915f'],
  lemon: ['var(--sm-tint-lemon)', '#6b7a00'],
  rose: ['var(--sm-tint-rose)', '#c63d7c'],
};

export function EmptyState({ icon: Icon, title, body, action, tone = 'lavender', compact, className }: EmptyStateProps) {
  const [bg, fg] = TONE[tone];
  return (
    <div className={cx('sm-empty', compact && 'sm-empty--compact', className)}>
      <div className="sm-empty__art" style={{ background: bg, color: fg }}>
        <Icon size={compact ? 28 : 40} strokeWidth={1.75} aria-hidden />
        <span className="sm-empty__spark" aria-hidden />
      </div>
      <div className="sm-empty__title">{title}</div>
      {body && <p className="sm-empty__body">{body}</p>}
      {action && <div className="sm-empty__action">{action}</div>}
    </div>
  );
}
