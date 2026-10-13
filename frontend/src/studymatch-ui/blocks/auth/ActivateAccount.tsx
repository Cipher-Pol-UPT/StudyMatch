import { useTranslation } from 'react-i18next';
import type { FormFeedbackProps } from '../../../types/forms';
import { FormFields } from '../FormFields';
export interface ActivateAccountProps extends FormFeedbackProps {
  onSubmit: (input: { personalEmail: string; password: string; confirmPassword: string }) => Promise<void>;
}
export function ActivateAccount({ onSubmit, fieldErrors }: ActivateAccountProps) {
  const { t } = useTranslation();
  return <FormFields title={t('auth.activateAccount')} fields={[
    { name: 'personalEmail', label: 'fields.personalEmail', type: 'email' },
    { name: 'password', label: 'fields.newPassword', type: 'password' },
    { name: 'confirmPassword', label: 'fields.confirmPassword', type: 'password' },
  ]} submitLabel="common.submit" fieldErrors={fieldErrors}
    onSubmit={v => onSubmit({ personalEmail: v.personalEmail, password: v.password, confirmPassword: v.confirmPassword })} />;
}
