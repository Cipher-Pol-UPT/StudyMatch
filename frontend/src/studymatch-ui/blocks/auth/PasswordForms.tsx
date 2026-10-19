import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { ChangePasswordInput,FormFeedbackProps,ResetPasswordInput } from '../../../types/forms';
import { Button } from '../../components/button/Button';
import { PasswordField } from '../../components/field/PasswordField';
import { TextField } from '../../components/field/TextField';
import { Banner } from '../../components/overlay/Banner';
import type { PasswordRule } from '../../utils/password';
import { useFormFeedback } from '../../utils/useFormFeedback';
import { FormFields } from '../FormFields';
import { AuthHeader } from './AuthLayout';
export interface ChangePasswordFormProps extends FormFeedbackProps {
  onSubmit: (input: ChangePasswordInput) => Promise<void>; onCancel?: () => void; showCurrentPassword?: boolean;
}
export function ChangePasswordForm({ onSubmit, onCancel, showCurrentPassword = true, fieldErrors }: ChangePasswordFormProps) {
  const { t } = useTranslation();
  return <FormFields title={t('auth.changePassword')} fields={[
    ...(showCurrentPassword ? [{ name: 'current', label: 'fields.currentPassword', type: 'password' }] : []),
    { name: 'password', label: 'fields.newPassword', type: 'password' },
    { name: 'confirmPassword', label: 'fields.confirmPassword', type: 'password' },
  ]} submitLabel="common.save" fieldErrors={fieldErrors} onCancel={onCancel}
    onSubmit={v => onSubmit({ current: showCurrentPassword ? v.current : undefined, password: v.password, confirmPassword: v.confirmPassword })} />;
}
export interface ForgotPasswordFormProps extends FormFeedbackProps { onSubmit: (email: string) => Promise<void>; onBack?: () => void }
export function ForgotPasswordForm({ onSubmit, onBack, fieldErrors }: ForgotPasswordFormProps) {
  const { t } = useTranslation();
  return <FormFields title={t('auth.forgotPassword')} fields={[{ name: 'email', label: 'fields.institutionalEmail', type: 'email' }]}
    submitLabel="auth.sendCode" onCancel={onBack} fieldErrors={fieldErrors} onSubmit={v => onSubmit(v.email)} />;
}
export interface ResetPasswordFormProps extends FormFeedbackProps {
  email: string;
  onSubmit: (input: ResetPasswordInput) => Promise<void>;
  onResendCode: () => Promise<void>;
  onBack?: () => void;
  passwordRules?: PasswordRule[];
}
export function ResetPasswordForm({ email, onSubmit, onResendCode, onBack, passwordRules, fieldErrors }: ResetPasswordFormProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback(fieldErrors);
  const resend = useFormFeedback();
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordsDiffer, setPasswordsDiffer] = useState(false);

  function submit() {
    if (newPassword !== confirmPassword) {
      setPasswordsDiffer(true);
      return;
    }
    void feedback.submit(() => onSubmit({ code, newPassword }));
  }

  return <div>
    <AuthHeader title={t('auth.resetPassword')} subtitle={t('auth.codeSent', { email })} />
    <form className="sm-auth__form" noValidate onSubmit={event => {
      event.preventDefault(); submit();
    }}>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      {resend.message && <Banner tone="danger">{resend.message}</Banner>}
      <TextField label={t('fields.code')} inputMode="numeric" autoComplete="one-time-code" value={code}
        disabled={feedback.busy} error={feedback.fieldError('code')} onChange={event => setCode(event.target.value)} />
      <PasswordField label={t('fields.newPassword')} autoComplete="new-password" rules={passwordRules} value={newPassword}
        disabled={feedback.busy} error={feedback.fieldError('newPassword')}
        onChange={event => { setNewPassword(event.target.value); setPasswordsDiffer(false); }} />
      <PasswordField label={t('fields.confirmPassword')} autoComplete="new-password" value={confirmPassword}
        disabled={feedback.busy} error={passwordsDiffer ? t('auth.passwordsDoNotMatch') : undefined}
        onChange={event => { setConfirmPassword(event.target.value); setPasswordsDiffer(false); }} />
      <Button type="submit" loading={feedback.busy}>{t('auth.resetPassword')}</Button>
      <Button variant="secondary" loading={resend.busy} disabled={feedback.busy} onClick={() => void resend.submit(onResendCode)}>
        {t('auth.resendCode')}
      </Button>
      {onBack && <Button variant="ghost" disabled={feedback.busy} onClick={onBack}>{t('auth.backToLogin')}</Button>}
    </form>
  </div>;
}
