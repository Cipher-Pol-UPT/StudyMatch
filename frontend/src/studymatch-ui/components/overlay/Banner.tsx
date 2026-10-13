import { AlertTriangle,CheckCircle2,Info,X,XCircle } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import type { IconComponent } from '../../utils/shared';
import './overlay.css';

export type BannerTone = 'info' | 'success' | 'warning' | 'danger' | 'neutral';

const TONE_ICON: Record<BannerTone, IconComponent> = { info: Info, success: CheckCircle2, warning: AlertTriangle, danger: XCircle, neutral: Info };

export interface BannerProps {
  tone?: BannerTone;
  title?: ReactNode;
  children?: ReactNode;
  /** Button or link on the right. */
  action?: ReactNode;
  /** Shows a close button. */
  onDismiss?: () => void;
  /** Replace the tone icon (or pass `null` to hide it). */
  icon?: IconComponent | null;
  className?: string;
}

/** Inline message about the page or a form. Danger banners are announced immediately. */
export function Banner({ tone = 'info', title, children, action, onDismiss, icon, className }: BannerProps) {
  useTranslation();
  const Icon = icon === null ? null : (icon ?? TONE_ICON[tone]);
  return (
    <div className={cx('sm-banner', `sm-banner--${tone}`, className)} role={tone === 'danger' ? 'alert' : 'status'}>
      {Icon && <Icon className="sm-banner__icon" size={20} strokeWidth={2.25} aria-hidden />}
      <div className="sm-banner__body">
        {title && <b className="sm-banner__title">{title}</b>}
        {children}
      </div>
      {action && <div className="sm-banner__action">{action}</div>}
      {onDismiss && (
        <button type="button" className="sm-banner__close" onClick={onDismiss} aria-label={i18n.t('ui.dismiss')}>
          <X size={16} strokeWidth={2.5} />
        </button>
      )}
    </div>
  );
}
