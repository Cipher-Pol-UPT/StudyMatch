import type { PointerEvent, ReactNode } from 'react';
import { cx } from '../../utils/cx';
import type { IconComponent } from '../../utils/shared';
import './field.css';

export interface FieldShellProps {
  /** id of the control inside. Used to wire the label and the hint. */
  id: string;
  label: ReactNode;
  hint?: ReactNode;
  /** Replaces the hint and turns the field red. */
  error?: ReactNode;
  /** Adds a quiet "Optional" tag next to the label. */
  optional?: boolean;
  icon?: IconComponent;
  suffix?: ReactNode;
  disabled?: boolean;
  /** Align content to the top (multi-line controls). */
  top?: boolean;
  /** Right side of the hint row, e.g. a character counter. */
  aside?: ReactNode;
  /** Extra content under the hint, e.g. a password checklist. */
  below?: ReactNode;
  className?: string;
  children: ReactNode;
}

/**
 * The filled, iOS-style field box with an inline label.
 * Use it to wrap any custom control so it matches TextField.
 */
export function Field({ id, label, hint, error, optional, icon: Icon, suffix, disabled, top, aside, below, className, children }: FieldShellProps) {
  const hintId = `${id}-hint`;
  const focusControl = (e: PointerEvent<HTMLDivElement>) => {
    const target = e.target as HTMLElement;
    if (target.closest('button, a, input, select, textarea, label')) return;
    e.preventDefault();
    (document.getElementById(id) as HTMLElement | null)?.focus();
  };
  return (
    <div className={cx('sm-field', !!error && 'sm-field--error', disabled && 'sm-field--disabled', top && 'sm-field--top', className)}>
      <div className="sm-field__box" onPointerDown={focusControl}>
        {Icon && (
          <span className="sm-field__affix">
            <Icon size={20} strokeWidth={2} aria-hidden />
          </span>
        )}
        <span className="sm-field__inner">
          <label className="sm-field__label" htmlFor={id}>
            {label}
            {optional && <span className="sm-field__optional">Optional</span>}
          </label>
          {children}
        </span>
        {suffix && <span className="sm-field__affix sm-field__affix--end">{suffix}</span>}
      </div>
      {(error || hint || aside) && (
        <div className="sm-field__hint">
          {(error || hint) && (
            <span id={hintId} role={error ? 'alert' : undefined}>
              {error || hint}
            </span>
          )}
          {aside}
        </div>
      )}
      {below}
    </div>
  );
}

/** aria-describedby value for a control inside <Field>. */
export const describedBy = (id: string, hint?: ReactNode, error?: ReactNode) => (hint || error ? `${id}-hint` : undefined);
