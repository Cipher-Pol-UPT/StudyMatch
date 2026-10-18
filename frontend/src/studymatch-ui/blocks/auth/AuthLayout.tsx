import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { AchievementBadge } from '../../achievements/Achievement';
import { Logo } from '../../components/brand/Logo';
import { cx } from '../../utils/cx';
import './auth.css';

export interface AuthLayoutProps {
  children: ReactNode;
  headline?: ReactNode;
  tagline?: ReactNode;
  art?: ReactNode;
  topRight?: ReactNode;
  footer?: ReactNode;
  embedded?: boolean;
  centered?: boolean;
  className?: string;
}

export function AuthLayout({
  children,
  headline = i18n.t('login.headline'),
  tagline = i18n.t('login.tagline'),
  art,
  topRight,
  footer,
  embedded,
  centered,
  className,
}: AuthLayoutProps) {
  useTranslation();
  return (
    <div className={cx('sm-auth', embedded && 'sm-auth--embedded', centered && 'sm-auth--centered', className)}>
      <aside className="sm-auth__brand">
        <div className="sm-auth__mesh" aria-hidden>
          <i />
          <i />
          <i />
        </div>
        <div className="sm-auth__logo">
          <Logo size={34} inverted />
        </div>
        <div>
          <h1 className="sm-auth__headline">{headline}</h1>
          {tagline && <p className="sm-auth__sub">{tagline}</p>}
        </div>
        {!centered && (art ?? (
          <div className="sm-auth__art" aria-hidden>
            <AchievementBadge id="MENTOR" size={88} />
            <AchievementBadge id="FIRST_STUDYROOM" size={104} />
            <AchievementBadge id="TOP_RATED" size={80} />
          </div>
        ))}
        {!centered && (
          <div className="sm-auth__foot">{footer ?? <span>© {new Date().getFullYear()} StudyMatch</span>}</div>
        )}
      </aside>
      <main className="sm-auth__panel">
        <div className="sm-auth__panel-top">
          <span />
          {topRight}
        </div>
        <div className="sm-auth__panel-body">
          <div className="sm-auth__card">{children}</div>
        </div>
      </main>
    </div>
  );
}

export interface AuthHeaderProps {
  title: ReactNode;
  subtitle?: ReactNode;
}

export function AuthHeader({ title, subtitle }: AuthHeaderProps) {
  useTranslation();
  return (
    <header>
      <h2 className="sm-auth__title">{title}</h2>
      {subtitle && <p className="sm-auth__subtitle">{subtitle}</p>}
    </header>
  );
}
