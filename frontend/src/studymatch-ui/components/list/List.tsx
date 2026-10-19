import { ChevronRight } from 'lucide-react';
import type { CSSProperties,ElementType,ReactNode } from 'react';
import { cx } from '../../utils/cx';
import type { IconComponent } from '../../utils/shared';
import './list.css';

export interface ListRowProps {
  leading?: ReactNode;
  title: ReactNode;
  subtitle?: ReactNode;
  value?: ReactNode;
  valueSub?: ReactNode;
  trailing?: ReactNode;
  chevron?: boolean;
  onClick?: () => void;
  href?: string;
  linkAs?: ElementType;
  compact?: boolean;
  className?: string;
}

export function ListRow({ leading, title, subtitle, value, valueSub, trailing, chevron, onClick, href, linkAs, compact, className }: ListRowProps) {
  const body = (
    <>
      {leading}
      <div className="sm-row__main">
        <div className="sm-row__title">{title}</div>
        {subtitle && <div className="sm-row__sub">{subtitle}</div>}
      </div>
      {(value !== undefined || trailing || chevron) && (
        <div className="sm-row__trail">
          {value !== undefined && (
            <div className="sm-row__value">
              {value}
              {valueSub && <small>{valueSub}</small>}
            </div>
          )}
          {trailing}
          {chevron && <ChevronRight className="sm-row__chevron" size={20} strokeWidth={2.25} aria-hidden />}
        </div>
      )}
    </>
  );
  const cls = cx('sm-row', compact && 'sm-row--compact', className);
  if (href) {
    const Link = linkAs ?? 'a';
    return (
      <Link className={cls} href={href} to={linkAs ? href : undefined}>
        {body}
      </Link>
    );
  }
  if (onClick) {
    return (
      <button type="button" className={cls} onClick={onClick}>
        {body}
      </button>
    );
  }
  return <div className={cls}>{body}</div>;
}

export interface ListProps {
  children: ReactNode;
  title?: ReactNode;
  plain?: boolean;
  dividerInset?: number;
  className?: string;
}

export function List({ children, title, plain, dividerInset, className }: ListProps) {
  const list = (
    <div className={cx('sm-list', plain && 'sm-list--plain', !title && className)} style={dividerInset !== undefined ? ({ ['--sm-row-inset' as string]: `${dividerInset}px` } as CSSProperties) : undefined}>
      {children}
    </div>
  );
  if (!title) return list;
  return (
    <section className={cx('sm-list-section', className)}>
      <div className="sm-eyebrow sm-list-section__title">{title}</div>
      {list}
    </section>
  );
}

export interface IconTileProps {
  icon: IconComponent;
  color?: string;
  bg?: string;
  size?: number;
  shape?: 'circle' | 'square';
}

export function IconTile({ icon: Icon, color = 'var(--sm-black)', bg = 'var(--sm-fill)', size = 44, shape = 'circle' }: IconTileProps) {
  return (
    <span className={cx('sm-icon-tile', shape === 'square' && 'sm-icon-tile--square')} style={{ width: size, height: size, background: bg, color }}>
      <Icon size={size * 0.45} strokeWidth={2.25} aria-hidden />
    </span>
  );
}
