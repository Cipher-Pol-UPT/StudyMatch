import { useTranslation } from 'react-i18next';
import type { ChangePasswordInput,FormFeedbackProps } from '../../../types/forms';
import { FormFields } from '../FormFields';
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
    submitLabel="auth.sendLink" onCancel={onBack} fieldErrors={fieldErrors} onSubmit={v => onSubmit(v.email)} />;
}
export interface ResetPasswordFormProps extends FormFeedbackProps { token: string; onSubmit: (token: string, password: string, confirmPassword: string) => Promise<void> }
export function ResetPasswordForm({ token, onSubmit, fieldErrors }: ResetPasswordFormProps) {
  const { t } = useTranslation();
  return <FormFields title={t('auth.resetPassword')} fields={[
    { name: 'password', label: 'fields.newPassword', type: 'password' },
    { name: 'confirmPassword', label: 'fields.confirmPassword', type: 'password' },
  ]} submitLabel="common.save" fieldErrors={fieldErrors} onSubmit={v => onSubmit(token, v.password, v.confirmPassword)} />;
}
