import { Lock,MailCheck } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { FormFeedbackProps } from '../../../types/forms';
import { Button } from '../../components/button/Button';
import { Banner } from '../../components/overlay/Banner';
import type { IconComponent } from '../../utils/shared';
import { useFormFeedback } from '../../utils/useFormFeedback';
import './auth.css';
export interface AuthStatusProps { icon: IconComponent; title: ReactNode; children?: ReactNode; actions?: ReactNode; tone?: string; plain?: boolean }
export function AuthStatus({ icon: Icon, title, children, actions }: AuthStatusProps) {
  return <div className="sm-auth__status" role="status"><Icon aria-hidden /><h2>{title}</h2>{children}{actions}</div>;
}
export interface CheckEmailProps extends FormFeedbackProps { email: string; onSubmit?: () => Promise<void>; onBack?: () => void; canResend?: boolean }
export function CheckEmail({ email, onSubmit, onBack, canResend = false, fieldErrors }: CheckEmailProps) {
  const { t } = useTranslation(); const feedback = useFormFeedback(fieldErrors);
  return <AuthStatus icon={MailCheck} title={t('auth.checkEmail')}>
    <p>{email}</p>{feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
    {feedback.fieldError('email') && <p role="alert">{feedback.fieldError('email')}</p>}
    {onSubmit && <Button disabled={!canResend} loading={feedback.busy} onClick={() => void feedback.submit(onSubmit)}>{t('auth.resend')}</Button>}
    {onBack && <Button onClick={onBack}>{t('common.back')}</Button>}
  </AuthStatus>;
}
export interface BlockedNoticeProps { message?: ReactNode; contact?: { label: string; href: string }; onBack?: () => void }
export function BlockedNotice({ message, contact, onBack }: BlockedNoticeProps) {
  const { t } = useTranslation();
  return <AuthStatus icon={Lock} title={t('accountStatuses.BLOCKED')}>{message}
    {contact && <a href={contact.href}>{contact.label}</a>}{onBack && <Button onClick={onBack}>{t('common.back')}</Button>}
  </AuthStatus>;
}
export interface VerifyEmailProps extends FormFeedbackProps { token: string; onSubmit: (token: string) => Promise<void> }
export function VerifyEmail({ token, onSubmit, fieldErrors }: VerifyEmailProps) {
  const { t } = useTranslation(); const feedback = useFormFeedback(fieldErrors);
  return <AuthStatus icon={MailCheck} title={t('auth.verifyEmail')}>
    {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
    {feedback.fieldError('token') && <p role="alert">{feedback.fieldError('token')}</p>}
    <Button loading={feedback.busy} onClick={() => void feedback.submit(() => onSubmit(token))}>{t('common.confirm')}</Button>
  </AuthStatus>;
}
