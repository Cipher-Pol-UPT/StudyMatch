import { AtSign, CheckCircle2, KeyRound, ShieldAlert } from 'lucide-react';
import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { useOptionalAuth } from '../../auth/AuthProvider';
import type { ChangePasswordResult, ResetPasswordResult, TokenCheck } from '../../auth/types';
import { Button } from '../../components/button/Button';
import { PasswordField } from '../../components/field/PasswordField';
import { TextField } from '../../components/field/TextField';
import { Banner } from '../../components/overlay/Banner';
import { Spinner } from '../../components/progress/Progress';
import { isPasswordValid } from '../../utils/password';
import { AuthHeader } from './AuthLayout';
import { isEmail } from './LoginForm';
import { AuthStatus, CheckEmail } from './StatusScreens';
import './auth.css';

// ─── Change password ───────────────────────────────────────
export interface ChangePasswordFormProps {
  /** `first-login` hides the current password (the one-time password was just verified). */
  mode?: 'default' | 'first-login';
  onSuccess?: () => void;
  onCancel?: () => void;
  /** Use your own call instead of `useAuth().changePassword`. */
  onSubmit?: (input: { current?: string; next: string }) => Promise<ChangePasswordResult>;
  /** Show the success screen (default mode only). */
  showSuccess?: boolean;
  title?: ReactNode;
  subtitle?: ReactNode;
  hideHeader?: boolean;
}

/** Change Password use case, with the live policy checklist and every error scenario. */
export function ChangePasswordForm({ mode = 'default', onSuccess, onCancel, onSubmit, showSuccess = true, title, subtitle, hideHeader }: ChangePasswordFormProps) {
  const auth = useOptionalAuth();
  const forced = mode === 'first-login';
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState<{ current?: string; next?: string; confirm?: string; form?: string }>({});
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: typeof errors = {};
    if (!forced && !current) errs.current = 'Enter your current password.';
    if (!isPasswordValid(next, { current: forced ? undefined : current })) errs.next = 'Choose a password that meets every requirement below.';
    if (!confirm) errs.confirm = 'Type the new password again.';
    else if (confirm !== next) errs.confirm = 'The passwords don’t match.';
    setErrors(errs);
    if (Object.keys(errs).length) return;

    const run = onSubmit ?? auth?.changePassword;
    if (!run) throw new Error('ChangePasswordForm needs <AuthProvider> or an onSubmit prop.');
    setBusy(true);
    try {
      const r = await run({ current: forced ? undefined : current, next });
      if (r.ok) {
        if (forced || !showSuccess) onSuccess?.();
        else setDone(true);
        return;
      }
      if (r.error === 'wrong-current') setErrors({ current: 'Your current password is incorrect.' });
      else if (r.error === 'same-as-current') setErrors({ next: 'The new password must be different from the current one.' });
      else setErrors({ next: r.message ?? 'This password doesn’t meet the policy.' });
    } catch (err) {
      setErrors({ form: err instanceof Error ? err.message : 'Something went wrong. Try again.' });
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return (
      <AuthStatus icon={CheckCircle2} tone="success" title="Password updated" actions={onSuccess && <Button size="lg" block onClick={onSuccess}>Done</Button>}>
        Use your new password the next time you log in. We also sent a confirmation to your institutional email.
      </AuthStatus>
    );
  }

  return (
    <div>
      {!hideHeader && (
        <AuthHeader
          title={title ?? (forced ? 'Choose your password' : 'Change password')}
          subtitle={subtitle ?? (forced ? 'Your one-time password worked. Now pick a password only you know; the one-time password stops working.' : 'You’ll stay logged in on this device.')}
        />
      )}
      <form className="sm-auth__form" onSubmit={submit} noValidate>
        {errors.form && <Banner tone="danger">{errors.form}</Banner>}
        {!forced && (
          <PasswordField label="Current password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} error={errors.current} autoFocus />
        )}
        <PasswordField
          label="New password"
          autoComplete="new-password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          rules
          strength
          currentPassword={forced ? undefined : current || undefined}
          error={errors.next}
          autoFocus={forced}
        />
        <PasswordField label="Confirm new password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} error={errors.confirm} />
        <Button type="submit" size="lg" block loading={busy} style={{ marginTop: 6 }}>
          {forced ? 'Save and continue' : 'Update password'}
        </Button>
        {onCancel && (
          <Button variant="ghost" size="lg" block onClick={onCancel}>
            Cancel
          </Button>
        )}
      </form>
    </div>
  );
}

// ─── Forgot password ───────────────────────────────────────
export interface ForgotPasswordFormProps {
  onBack?: () => void;
  onSubmit?: (email: string) => Promise<void>;
  defaultEmail?: string;
}

/** Sends a reset link. Always shows the same confirmation, so nobody can probe which emails exist. */
export function ForgotPasswordForm({ onBack, onSubmit, defaultEmail = '' }: ForgotPasswordFormProps) {
  const auth = useOptionalAuth();
  const [email, setEmail] = useState(defaultEmail);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const run = onSubmit ?? auth?.adapter.requestPasswordReset.bind(auth.adapter);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!isEmail(email)) {
      setError('Enter the institutional email of your account.');
      return;
    }
    if (!run) throw new Error('ForgotPasswordForm needs <AuthProvider> or an onSubmit prop.');
    setBusy(true);
    try {
      await run(email.trim());
      setSent(true);
    } finally {
      setBusy(false);
    }
  };

  if (sent && run) {
    return (
      <CheckEmail
        email={email.trim()}
        description="If an account exists for this email, we sent a link to choose a new password. It expires in 30 minutes and can be used once."
        onResend={() => run(email.trim())}
        onBack={onBack}
      />
    );
  }

  return (
    <div>
      <AuthHeader title="Forgot your password?" subtitle="Enter your institutional email and we’ll send you a link to choose a new one." />
      <form className="sm-auth__form" onSubmit={submit} noValidate>
        <TextField label="Institutional email" type="email" icon={AtSign} autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} error={error} autoFocus />
        <Button type="submit" size="lg" block loading={busy}>
          Send reset link
        </Button>
        {onBack && (
          <Button variant="ghost" size="lg" block onClick={onBack}>
            Back to log in
          </Button>
        )}
      </form>
    </div>
  );
}

// ─── Reset password (landing page of the link) ─────────────
export interface ResetPasswordFormProps {
  token: string | null;
  /** Go to the login page after success or from an error. */
  onDone?: () => void;
  onRequestNewLink?: () => void;
  check?: (token: string) => Promise<TokenCheck>;
  onSubmit?: (token: string, password: string) => Promise<ResetPasswordResult>;
}

export function ResetPasswordForm({ token, onDone, onRequestNewLink, check, onSubmit }: ResetPasswordFormProps) {
  const auth = useOptionalAuth();
  const [state, setState] = useState<'checking' | TokenCheck | 'done'>('checking');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState<{ next?: string; confirm?: string }>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    const run = check ?? auth?.adapter.checkResetToken?.bind(auth.adapter);
    if (!token) setState('invalid');
    else if (!run) setState('valid');
    else run(token).then((s) => alive && setState(s));
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: typeof errors = {};
    if (!isPasswordValid(next)) errs.next = 'Choose a password that meets every requirement below.';
    if (confirm !== next) errs.confirm = 'The passwords don’t match.';
    setErrors(errs);
    if (Object.keys(errs).length || !token) return;
    const run = onSubmit ?? auth?.adapter.resetPassword.bind(auth.adapter);
    if (!run) throw new Error('ResetPasswordForm needs <AuthProvider> or an onSubmit prop.');
    setBusy(true);
    try {
      const r = await run(token, next);
      if (r.ok) setState('done');
      else if (r.error === 'policy') setErrors({ next: r.message ?? 'This password doesn’t meet the policy.' });
      else setState(r.error);
    } finally {
      setBusy(false);
    }
  };

  if (state === 'checking')
    return (
      <div className="sm-auth__status" role="status">
        <Spinner size={40} label="Checking your link" />
      </div>
    );
  if (state === 'done')
    return (
      <AuthStatus icon={CheckCircle2} tone="success" title="Password updated" actions={onDone && <Button size="lg" block onClick={onDone}>Log in</Button>}>
        You can now log in with your new password.
      </AuthStatus>
    );
  if (state === 'expired' || state === 'invalid')
    return (
      <AuthStatus
        icon={ShieldAlert}
        tone="warning"
        plain
        title={state === 'expired' ? 'This link has expired' : 'This link isn’t valid'}
        actions={
          <>
            {onRequestNewLink && (
              <Button size="lg" block onClick={onRequestNewLink}>
                Request a new link
              </Button>
            )}
            {onDone && (
              <Button variant="ghost" size="lg" block onClick={onDone}>
                Back to log in
              </Button>
            )}
          </>
        }
      >
        {state === 'expired' ? 'Reset links last 30 minutes. Ask for a new one.' : 'It may have been used already. Ask for a new one.'}
      </AuthStatus>
    );

  return (
    <div>
      <AuthHeader title="Choose a new password" subtitle="Pick something you don’t use anywhere else." />
      <form className="sm-auth__form" onSubmit={submit} noValidate>
        <PasswordField label="New password" icon={KeyRound} autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} rules strength error={errors.next} autoFocus />
        <PasswordField label="Confirm new password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} error={errors.confirm} />
        <Button type="submit" size="lg" block loading={busy}>
          Save new password
        </Button>
      </form>
    </div>
  );
}
