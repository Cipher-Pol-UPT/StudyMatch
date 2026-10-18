import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { requestPasswordReset, resetPassword } from '../../api/auth';
import type { BackendError } from '../../api/errors';
import { ForgotPasswordForm, ResetPasswordForm, useToast, type PasswordRule } from '../../studymatch-ui';
import type { ResetPasswordInput } from '../../types/forms';

const PASSWORD_RULES: PasswordRule[] = [
  { id: 'length', labelKey: 'passwordRules.length', test: password => password.length >= 8 },
  { id: 'letter', labelKey: 'passwordRules.letter', test: password => /\p{L}/u.test(password) },
  { id: 'number', labelKey: 'passwordRules.number', test: password => /\d/.test(password) },
];

const PASSWORDS_DO_NOT_MATCH: BackendError = {
  code: 'VALIDATION_FAILED',
  fieldErrors: [{ field: 'confirmPassword', code: 'PASSWORDS_DO_NOT_MATCH' }],
};

export interface ForgotPasswordProps {
  onPasswordReset: () => void;
  onBack: () => void;
}

export function ForgotPassword({ onPasswordReset, onBack }: ForgotPasswordProps) {
  const { t } = useTranslation();
  const toast = useToast();
  const [email, setEmail] = useState<string | null>(null);

  async function sendCode(institutionalEmail: string) {
    await requestPasswordReset(institutionalEmail);
    setEmail(institutionalEmail.trim());
  }

  async function sendNewCode(institutionalEmail: string) {
    await requestPasswordReset(institutionalEmail);
    toast.info(t('auth.newCodeSent'));
  }

  async function changePassword(institutionalEmail: string, { code, newPassword, confirmPassword }: ResetPasswordInput) {
    if (newPassword !== confirmPassword) {
      throw PASSWORDS_DO_NOT_MATCH;
    }
    await resetPassword({ email: institutionalEmail, code, newPassword });
    onPasswordReset();
  }

  if (email === null) {
    return <ForgotPasswordForm onSubmit={sendCode} onBack={onBack} />;
  }

  return (
    <ResetPasswordForm
      email={email}
      passwordRules={PASSWORD_RULES}
      onSubmit={input => changePassword(email, input)}
      onResendCode={() => sendNewCode(email)}
      onBack={onBack}
    />
  );
}
