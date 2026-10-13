import { ArrowDownRight,ArrowUpRight } from 'lucide-react';
import { forwardRef,type ElementType,type HTMLAttributes,type KeyboardEvent,type ReactNode } from 'react';
import { cx } from '../../utils/cx';
import './card.css';
import { isDarkTone } from './Card.shared';

export type CardTone = 'white' | 'gray' | 'lavender' | 'sky' | 'peach' | 'mint' | 'lemon' | 'rose' | 'violet' | 'black' | 'ink' | 'gradient';

export interface CardProps extends Omit<HTMLAttributes<HTMLElement>, 'title'> {
  tone?: CardTone;
  outline?: boolean;
  padding?: 'none' | 'sm' | 'md' | 'lg';
  /** Element to render: 'div' (default), 'section', 'article', 'li'… */
  as?: ElementType;
  /** Header title. */
  title?: ReactNode;
  /** Text under the title. */
  description?: ReactNode;
  /** Top-right action next to the title. */
  action?: ReactNode;
}

/**
 * The basic surface. Tints are for friendly grouping, dark tones for hero moments.
 * With `onClick`, the card becomes keyboard-operable (Enter / Space).
 */
export const Card = forwardRef<HTMLElement, CardProps>(function Card(
  { tone = 'white', outline, padding = 'md', as: As = 'div', title, description, action, onClick, onKeyDown, className, children, ...rest },
  ref,
) {
  const interactive = !!onClick;
  const handleKey = (e: KeyboardEvent<HTMLElement>) => {
    onKeyDown?.(e);
    if (interactive && (e.key === 'Enter' || e.key === ' ') && e.target === e.currentTarget) {
      e.preventDefault();
      (e.currentTarget as HTMLElement).click();
    }
  };
  return (
    <As
      ref={ref}
      className={cx(
        'sm-card',
        tone !== 'white' && `sm-card--${tone}`,
        isDarkTone(tone) && 'sm-card--on-dark',
        outline && 'sm-card--outline',
        padding !== 'md' && `sm-card--pad-${padding}`,
        interactive && 'sm-card--interactive',
        className,
      )}
      onClick={onClick}
      onKeyDown={handleKey}
      role={interactive ? 'button' : undefined}
      tabIndex={interactive ? 0 : undefined}
      {...rest}
    >
      {(title || action) && (
        <div className="sm-card__head">
          <div>
            {title && <h3 className="sm-card__title">{title}</h3>}
            {description && <p className="sm-card__desc">{description}</p>}
          </div>
          {action}
        </div>
      )}
      {children}
    </As>
  );
});

export interface StatCardProps {
  label: ReactNode;
  value: ReactNode;
  /** Small unit after the value ("%", "/20"). */
  unit?: ReactNode;
  caption?: ReactNode;
  /** Signed change, e.g. +4.2 or -1. Positive is green. */
  trend?: number;
  trendSuffix?: string;
  tone?: CardTone;
  action?: ReactNode;
  className?: string;
}

/** Large numeric headline card: one honest number, big. */
export function StatCard({ label, value, unit, caption, trend, trendSuffix = '%', tone = 'white', action, className }: StatCardProps) {
  return (
    <Card tone={tone} className={className}>
      <div className="sm-stat__label-row">
        <span className="sm-eyebrow">{label}</span>
        {action}
      </div>
      <div className="sm-stat__value">
        {value}
        {unit && <small>{unit}</small>}
      </div>
      {(caption || trend !== undefined) && (
        <div className="sm-stat__caption">
          {trend !== undefined && (
            <span className={cx('sm-stat__trend', trend >= 0 ? 'sm-stat__trend--up' : 'sm-stat__trend--down')}>
              {trend >= 0 ? <ArrowUpRight size={13} strokeWidth={2.5} aria-hidden /> : <ArrowDownRight size={13} strokeWidth={2.5} aria-hidden />}
              {trend > 0 ? '+' : ''}
              {trend}
              {trendSuffix}
            </span>
          )}
          {caption}
        </div>
      )}
    </Card>
  );
}
