import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { AchievementBadge } from '../../achievements/Achievement';
import { Logo } from '../../components/brand/Logo';
import { cx } from '../../utils/cx';
import './auth.css';

export interface AuthLayoutProps {
  children: ReactNode;
  /** Big line on the brand panel. */
  headline?: ReactNode;
  /** Supporting text on the brand panel. */
  tagline?: ReactNode;
  /** Replace the decorative art on the brand panel. */
  art?: ReactNode;
  /** Top-right of the form panel (e.g. a "Back to login" link or a language switch). */
  topRight?: ReactNode;
  /** Footer links on the brand panel. */
  footer?: ReactNode;
  /** Render inside a page (docs, previews) instead of full-screen. */
  embedded?: boolean;
  className?: string;
}

/** Split screen for every auth page: a brand panel and a calm form column. Stacks on mobile. */
export function AuthLayout({
  children,
  headline = i18n.t('ui.better_teams_start_here'),
  tagline = i18n.t('ui.studymatch_forms_balanced_groups_from_your_strengths_grades_and_a'),
  art,
  topRight,
  footer,
  embedded,
  className,
}: AuthLayoutProps) {
  useTranslation();
  return (
    <div className={cx('sm-auth', embedded && 'sm-auth--embedded', className)}>
      <aside className="sm-auth__brand">
        <div className="sm-auth__mesh" aria-hidden>
          <i />
          <i />
          <i />
        </div>
        <Logo size={34} inverted />
        <div>
          <h1 className="sm-auth__headline">{headline}</h1>
          {tagline && <p className="sm-auth__sub">{tagline}</p>}
        </div>
        {art ?? (
          <div className="sm-auth__art" aria-hidden>
            <AchievementBadge id="MENTOR" size={88} />
            <AchievementBadge id="FIRST_STUDYROOM" size={104} />
            <AchievementBadge id="TOP_RATED" size={80} />
          </div>
        )}
        <div className="sm-auth__foot">{footer ?? <span>Â© {new Date().getFullYear()} StudyMatch</span>}</div>
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
