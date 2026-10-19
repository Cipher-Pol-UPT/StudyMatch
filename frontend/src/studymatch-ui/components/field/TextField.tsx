import { forwardRef,useId,type InputHTMLAttributes,type ReactNode } from 'react';
import type { IconComponent } from '../../utils/shared';
import { Field } from './Field';
import { describedBy } from './Field.shared';

export interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size' | 'prefix'> {
  label: ReactNode;
  hint?: ReactNode;
  error?: ReactNode;
  optional?: boolean;
  icon?: IconComponent;
  suffix?: ReactNode;
  containerClassName?: string;
}

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
