import { Check, Eye, EyeOff } from 'lucide-react';
import { forwardRef, useId, useState, type ChangeEvent, type InputHTMLAttributes, type ReactNode } from 'react';
import { checkPassword, passwordStrength, STRENGTH_LABELS, STUDYMATCH_PASSWORD_RULES, type PasswordRule } from '../../utils/password';
import type { IconComponent } from '../../utils/shared';
import { Field, describedBy } from './Field';

export interface PasswordFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size' | 'type' | 'prefix'> {
  label?: ReactNode;
  hint?: ReactNode;
  error?: ReactNode;
  icon?: IconComponent;
  /**
   * Show a live checklist of the password policy under the field.
   * `true` uses the StudyMatch policy (8+ chars, letters and numbers, different from current).
   */
  rules?: boolean | PasswordRule[];
  /** The current password, so the "different from current" rule can be checked. */
  currentPassword?: string;
  /** Show a four-step strength meter. */
  strength?: boolean;
  containerClassName?: string;
}

const METER_COLORS = ['var(--sm-danger)', 'var(--sm-danger)', 'var(--sm-warning)', 'var(--sm-success)', 'var(--sm-success)'];

/** Password input with a show/hide toggle, an optional policy checklist and a strength meter. */
export const PasswordField = forwardRef<HTMLInputElement, PasswordFieldProps>(function PasswordField(
  { label = 'Password', hint, error, icon, rules, currentPassword, strength, id, containerClassName, className, onChange, value, defaultValue, disabled, ...rest },
  ref,
) {
  const auto = useId();
  const fid = id ?? auto;
  const [visible, setVisible] = useState(false);
  const [inner, setInner] = useState(String(defaultValue ?? ''));
  const current = value !== undefined ? String(value) : inner;

  const ruleSet = rules === true ? STUDYMATCH_PASSWORD_RULES : rules || null;
  const results = ruleSet ? checkPassword(current, { rules: ruleSet, current: currentPassword }) : [];
  const score = passwordStrength(current);

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (value === undefined) setInner(e.target.value);
    onChange?.(e);
  };

  const below = (
    <>
      {strength && current && (
        <div className="sm-pw-meter" aria-live="polite">
          {[1, 2, 3, 4].map((i) => (
            <i key={i} style={{ background: score >= i ? METER_COLORS[score] : undefined }} />
          ))}
          <span>{STRENGTH_LABELS[score]}</span>
        </div>
      )}
      {results.length > 0 && (
        <ul className="sm-pw-rules" aria-label="Password requirements">
          {results.map((r) => (
            <li key={r.id} data-ok={r.ok}>
              <i aria-hidden>
                <Check size={12} strokeWidth={3.5} />
              </i>
              {r.label}
              <span className="sm-visually-hidden">{r.ok ? ' (met)' : ' (not met)'}</span>
            </li>
          ))}
        </ul>
      )}
    </>
  );

  return (
    <Field
      id={fid}
      label={label}
      hint={hint}
      error={error}
      icon={icon}
      disabled={disabled}
      className={containerClassName}
      below={below}
      suffix={
        <button
          type="button"
          className="sm-pw-toggle"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
          aria-controls={fid}
          disabled={disabled}
        >
          {visible ? <EyeOff size={19} strokeWidth={2} /> : <Eye size={19} strokeWidth={2} />}
        </button>
      }
    >
      <input
        ref={ref}
        id={fid}
        type={visible ? 'text' : 'password'}
        className={['sm-field__input', className].filter(Boolean).join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(fid, hint, error)}
        autoComplete="current-password"
        spellCheck={false}
        value={value}
        defaultValue={value === undefined ? defaultValue : undefined}
        onChange={handleChange}
        disabled={disabled}
        {...rest}
      />
    </Field>
  );
});
