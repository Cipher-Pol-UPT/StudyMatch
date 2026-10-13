import type { ReactNode } from 'react';
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
  headline = 'Better teams start here.',
  tagline = 'StudyMatch forms balanced groups from your strengths, grades and attendance, so every project starts on the right foot.',
  art,
  topRight,
  footer,
  embedded,
  className,
}: AuthLayoutProps) {
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
            <AchievementBadge id="team-player" size={88} />
            <AchievementBadge id="know-yourself" size={104} />
            <AchievementBadge id="five-stars" size={80} />
          </div>
        )}
        <div className="sm-auth__foot">{footer ?? <span>© {new Date().getFullYear()} StudyMatch</span>}</div>
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
  return (
    <header>
      <h2 className="sm-auth__title">{title}</h2>
      {subtitle && <p className="sm-auth__subtitle">{subtitle}</p>}
    </header>
  );
}
