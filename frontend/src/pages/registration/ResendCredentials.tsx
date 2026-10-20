import { Send } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { resendCredentials } from '../../api/students';
import { Banner, Button, TextField, useFormFeedback, useToast } from '../../studymatch-ui';

export interface ResendCredentialsProps {
  studentNumber: number;
  personalEmail: string;
  onSent?: () => void;
}

export function ResendCredentials({ studentNumber, personalEmail, onSent }: ResendCredentialsProps) {
  const { t } = useTranslation();
  const toast = useToast();
  const feedback = useFormFeedback();
  const [email, setEmail] = useState(personalEmail);

  async function resend() {
    await resendCredentials(studentNumber, email.trim() === personalEmail ? undefined : email);
    toast.success(t('registration.credentialsResent'));
    onSent?.();
  }

  return (
    <form className="registration__resend" noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(resend); }}>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <TextField label={t('fields.personalEmail')} type="email" value={email} hint={t('registration.fixEmailHint')}
        disabled={feedback.busy} error={feedback.fieldError('personalEmail')} onChange={event => setEmail(event.target.value)} />
      <Button type="submit" variant="secondary" icon={Send} loading={feedback.busy}>{t('registration.resend')}</Button>
    </form>
  );
}
