import { Check, Minus } from 'lucide-react';
import { forwardRef, useEffect, useImperativeHandle, useRef, type InputHTMLAttributes, type ReactNode } from 'react';
import { cx } from '../../utils/cx';
import './selection.css';

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'children'> {
  /** Label shown next to the box. */
  children?: ReactNode;
  /** Secondary line under the label. */
  description?: ReactNode;
  /** "Some selected" state, e.g. for a select-all checkbox. */
  indeterminate?: boolean;
  /** Convenience callback with the boolean value. */
  onCheckedChange?: (checked: boolean) => void;
  invalid?: boolean;
}

/** A real <input type="checkbox"> with a custom face. Forms, refs and native events all work. */
export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox(
  { children, description, indeterminate, onCheckedChange, onChange, disabled, invalid, className, ...rest },
  ref,
) {
  const inner = useRef<HTMLInputElement>(null);
  useImperativeHandle(ref, () => inner.current as HTMLInputElement);
  useEffect(() => {
    if (inner.current) inner.current.indeterminate = !!indeterminate;
  }, [indeterminate]);
  return (
    <label className={cx('sm-check', disabled && 'sm-check--disabled', invalid && 'sm-check--invalid', className)}>
      <input
        ref={inner}
        type="checkbox"
        disabled={disabled}
        aria-invalid={invalid || undefined}
        onChange={(e) => {
          onChange?.(e);
          onCheckedChange?.(e.target.checked);
        }}
        {...rest}
      />
      <span className="sm-check__box" aria-hidden>
        {indeterminate ? <Minus size={15} strokeWidth={3.5} /> : <Check size={15} strokeWidth={3.5} />}
      </span>
      {(children || description) && (
        <span className="sm-choice__text">
          {children}
          {description && <span className="sm-choice__desc">{description}</span>}
        </span>
      )}
    </label>
  );
});
