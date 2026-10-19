import type { PointerEvent,ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import type { IconComponent } from '../../utils/shared';
import './field.css';

export interface FieldShellProps {
  id: string;
  label: ReactNode;
  hint?: ReactNode;
  error?: ReactNode;
  optional?: boolean;
  icon?: IconComponent;
  suffix?: ReactNode;
  disabled?: boolean;
  top?: boolean;
  aside?: ReactNode;
  below?: ReactNode;
  className?: string;
  children: ReactNode;
}

export function Field({ id, label, hint, error, optional, icon: Icon, suffix, disabled, top, aside, below, className, children }: FieldShellProps) {
  useTranslation();
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
            {optional && <span className="sm-field__optional"> {i18n.t('ui.optional')} </span>}
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
