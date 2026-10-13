import { X } from 'lucide-react';
import type { HTMLAttributes, MouseEvent, ReactNode } from 'react';
import { cx } from '../../utils/cx';
import type { IconComponent } from '../../utils/shared';
import './chip.css';

export interface ChipProps {
  children: ReactNode;
  /** Pressed / active filter state. */
  selected?: boolean;
  onClick?: (event: MouseEvent<HTMLButtonElement>) => void;
  icon?: IconComponent;
  /** Shows a remove (×) button. The chip and the × are separate buttons. */
  onRemove?: () => void;
  /** Label for the remove button. Defaults to "Remove <children>". */
  removeLabel?: string;
  size?: 'sm' | 'md';
  disabled?: boolean;
  className?: string;
}

/** Filter chip or removable tag. A chip without `onClick` is static (a label, not a button). */
export function Chip({ children, selected, onClick, icon: Icon, onRemove, removeLabel, size = 'md', disabled, className }: ChipProps) {
  const interactive = !!onClick;
  const content = (
    <>
      {Icon && <Icon size={size === 'sm' ? 14 : 16} strokeWidth={2.25} aria-hidden />}
      {children}
    </>
  );
  return (
    <span
      className={cx('sm-chip', size === 'sm' && 'sm-chip--sm', !interactive && 'sm-chip--static', onRemove && 'sm-chip--removable', className)}
      data-selected={!!selected}
    >
      {interactive ? (
        <button type="button" className="sm-chip__main" aria-pressed={!!selected} onClick={onClick} disabled={disabled}>
          {content}
        </button>
      ) : (
        <span className="sm-chip__main">{content}</span>
      )}
      {onRemove && (
        <button
          type="button"
          className="sm-chip__remove"
          aria-label={removeLabel ?? `Remove ${typeof children === 'string' ? children : ''}`.trim()}
          onClick={onRemove}
          disabled={disabled}
        >
          <X size={11} strokeWidth={3} />
        </button>
      )}
    </span>
  );
}

export function ChipGroup({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cx('sm-chip-group', className)} {...rest} />;
}
