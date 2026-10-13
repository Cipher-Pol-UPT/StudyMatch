import { Check,Eye,EyeOff } from 'lucide-react';
import { forwardRef,useId,useState,type ChangeEvent,type InputHTMLAttributes,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { checkPassword,type PasswordRule } from '../../utils/password';
import type { IconComponent } from '../../utils/shared';
import { Field } from './Field';
import { describedBy } from './Field.shared';

export interface PasswordFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size' | 'type' | 'prefix'> {
  label?: ReactNode;
  hint?: ReactNode;
  error?: ReactNode;
  icon?: IconComponent;
  /** Optional translated UX hints supplied by the caller. */
  rules?: PasswordRule[];
  containerClassName?: string;
}


/** Password input with a show/hide toggle, an optional hint checklist . */
export const PasswordField = forwardRef<HTMLInputElement, PasswordFieldProps>(function PasswordField(
  { label = i18n.t('ui.password'), hint, error, icon, rules, id, containerClassName, className, onChange, value, defaultValue, disabled, ...rest },
  ref,
) {
  useTranslation();
  const auto = useId();
  const fid = id ?? auto;
  const [visible, setVisible] = useState(false);
  const [inner, setInner] = useState(String(defaultValue ?? ''));
  const current = value !== undefined ? String(value) : inner;

  const results = rules ? checkPassword(current, rules) : [];

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (value === undefined) setInner(e.target.value);
    onChange?.(e);
  };

  const below = (
    <>
      {results.length > 0 && (
        <ul className="sm-pw-rules" aria-label={i18n.t('ui.password_requirements')}>
          {results.map((r) => (
            <li key={r.id} data-ok={r.ok}>
              <i aria-hidden>
                <Check size={12} strokeWidth={3.5} />
              </i>
              {i18n.t(r.labelKey)}
              <span className="sm-visually-hidden">{r.ok ? ' (met)' : i18n.t('ui.not_met')}</span>
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
          aria-label={visible ? i18n.t('ui.hide_password') : i18n.t('ui.show_password')}
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
