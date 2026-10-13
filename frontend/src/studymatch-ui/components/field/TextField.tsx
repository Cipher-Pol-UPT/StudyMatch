import { forwardRef,useId,type InputHTMLAttributes,type ReactNode } from 'react';
import type { IconComponent } from '../../utils/shared';
import { Field } from './Field';
import { describedBy } from './Field.shared';

export interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size' | 'prefix'> {
  label: ReactNode;
  hint?: ReactNode;
  /** Error message. Replaces the hint and sets aria-invalid. */
  error?: ReactNode;
  /** Shows an "Optional" tag next to the label. */
  optional?: boolean;
  /** Leading icon. */
  icon?: IconComponent;
  /** Trailing content: a unit, a button, a spinner. */
  suffix?: ReactNode;
  /** Class name for the outer wrapper (the input keeps `sm-field__input`). */
  containerClassName?: string;
}

/** Filled text field with an inline label. Works with any form library: the ref goes to the <input>. */
export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, hint, error, optional, icon, suffix, id, containerClassName, className, disabled, ...rest },
  ref,
) {
  const auto = useId();
  const fid = id ?? auto;
  return (
    <Field id={fid} label={label} hint={hint} error={error} optional={optional} icon={icon} suffix={suffix} disabled={disabled} className={containerClassName}>
      <input
        ref={ref}
        id={fid}
        className={['sm-field__input', className].filter(Boolean).join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(fid, hint, error)}
        disabled={disabled}
        {...rest}
      />
    </Field>
  );
});
