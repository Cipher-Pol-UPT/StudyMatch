import { ChevronDown } from 'lucide-react';
import { forwardRef, useId, type ReactNode, type SelectHTMLAttributes } from 'react';
import { cx } from '../../utils/cx';
import type { IconComponent } from '../../utils/shared';
import { Field, describedBy } from './Field';

export interface SelectOption {
  value: string;
  label: ReactNode;
  disabled?: boolean;
}

export interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'> {
  label: ReactNode;
  /** Options as data. You can also pass <option> children. */
  options?: SelectOption[];
  /** First, empty option shown when nothing is selected. */
  placeholder?: string;
  hint?: ReactNode;
  error?: ReactNode;
  optional?: boolean;
  icon?: IconComponent;
  containerClassName?: string;
}

/**
 * Native select in the filled field style. Native on purpose: it is accessible,
 * works on every phone picker and plays well with any form library.
 */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, options, placeholder, hint, error, optional, icon, id, containerClassName, className, children, disabled, ...rest },
  ref,
) {
  const auto = useId();
  const fid = id ?? auto;
  return (
    <Field id={fid} label={label} hint={hint} error={error} optional={optional} icon={icon} disabled={disabled} className={containerClassName}>
      <select
        ref={ref}
        id={fid}
        className={cx('sm-field__input', 'sm-field__select', className)}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(fid, hint, error)}
        disabled={disabled}
        {...rest}
      >
        {placeholder !== undefined && (
          <option value="" disabled>
            {placeholder}
          </option>
        )}
        {options?.map((o) => (
          <option key={o.value} value={o.value} disabled={o.disabled}>
            {o.label as string}
          </option>
        ))}
        {children}
      </select>
      <ChevronDown className="sm-field__chevron" size={18} strokeWidth={2.25} aria-hidden />
    </Field>
  );
});
