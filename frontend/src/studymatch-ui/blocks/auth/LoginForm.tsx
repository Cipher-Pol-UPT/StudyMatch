import { useTranslation } from 'react-i18next';
import type { FormFeedbackProps,LoginInput } from '../../../types/forms';
import { FormFields } from '../FormFields';
export interface LoginFormProps extends FormFeedbackProps {
  onSubmit: (input: LoginInput) => Promise<void>; onForgotPassword?: () => void;
}
export function LoginForm({ onSubmit, onForgotPassword, fieldErrors }: LoginFormProps) {
  const { t } = useTranslation();
  return <div><FormFields title={t('auth.login')} fields={[
    { name: 'email', label: 'fields.institutionalEmail', type: 'email' },
    { name: 'password', label: 'fields.password', type: 'password' },
  ]} submitLabel="auth.login" fieldErrors={fieldErrors} onSubmit={v => onSubmit({ email: v.email, password: v.password })} />
  {onForgotPassword && <button className="sm-auth__link" type="button" onClick={onForgotPassword}>{t('auth.forgotPassword')}</button>}</div>;
}
