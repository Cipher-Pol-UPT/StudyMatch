import { ArrowRight, AtSign } from 'lucide-react';
import { useRef, useState, type FormEvent, type ReactNode } from 'react';
import { useOptionalAuth } from '../../auth/AuthProvider';
import { nextStep, type AuthStep, type LoginResult, type Session } from '../../auth/types';
import { Button } from '../../components/button/Button';
import { PasswordField } from '../../components/field/PasswordField';
import { TextField } from '../../components/field/TextField';
import { Banner } from '../../components/overlay/Banner';
import { AuthHeader } from './AuthLayout';
import { BlockedNotice } from './StatusScreens';
import './auth.css';

export const isEmail = (v: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim());

export interface DemoAccount {
  label: string;
  email: string;
  password: string;
}

export interface LoginFormProps {
  /** Called after a successful login with the next mandatory step. */
  onAuthenticated?: (session: Session, step: AuthStep) => void;
  onForgotPassword?: () => void;
  /** Use your own call instead of `useAuth().login`. */
  onSubmit?: (email: string, password: string) => Promise<LoginResult>;
  /** Message above the form, e.g. "You’ve been logged out." */
  notice?: ReactNode;
  title?: ReactNode;
  subtitle?: ReactNode;
  defaultEmail?: string;
  emailPlaceholder?: string;
  /** Quick-fill accounts for demos and QA (mock adapter). */
  demoAccounts?: DemoAccount[];
  /** Ask for a verified personal email after login (affects the computed next step). */
  requirePersonalEmail?: boolean;
  contact?: { label: string; href: string };
}

/**
 * Log In use case: institutional email + password.
 * Handles invalid credentials, blocked accounts, temporary locks and the
 * one-time password (first login → change password).
 */
export function LoginForm({
  onAuthenticated,
  onForgotPassword,
  onSubmit,
  notice,
  title = 'Log in',
  subtitle = 'Use your institutional email. First time here? Use the one-time password we emailed you.',
  defaultEmail = '',
  emailPlaceholder = 'number@students.uni.example',
  demoAccounts,
  requirePersonalEmail = true,
  contact,
}: LoginFormProps) {
  const auth = useOptionalAuth();
  const [email, setEmail] = useState(defaultEmail);
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [result, setResult] = useState<LoginResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [shake, setShake] = useState(0);
  const passwordRef = useRef<HTMLInputElement>(null);

  const submit = async (e?: FormEvent) => {
    e?.preventDefault();
    const next: typeof errors = {};
    if (!email.trim()) next.email = 'Enter your institutional email.';
    else if (!isEmail(email)) next.email = 'That doesn’t look like an email address.';
    if (!password) next.password = 'Enter your password.';
    setErrors(next);
    if (next.email || next.password) return;

    const run = onSubmit ?? auth?.login;
    if (!run) throw new Error('LoginForm needs <AuthProvider> or an onSubmit prop.');
    setBusy(true);
    setResult(null);
    try {
      const r = await run(email, password);
      setResult(r);
      if (r.status === 'ok') onAuthenticated?.(r.session, nextStep(r.session, { requirePersonalEmail }));
      else if (r.status === 'invalid') {
        setPassword('');
        setShake((s) => s + 1);
        requestAnimationFrame(() => passwordRef.current?.focus());
      }
    } catch {
      setResult({ status: 'invalid' });
    } finally {
      setBusy(false);
    }
  };

  if (result?.status === 'blocked') {
    return <BlockedNotice message={result.message} contact={contact} onBack={() => setResult(null)} />;
  }

  return (
    <div>
      <AuthHeader title={title} subtitle={subtitle} />
      <form className="sm-auth__form" onSubmit={submit} noValidate>
        {notice}
        {result?.status === 'invalid' && (
          <Banner tone="danger" title="Couldn’t log you in">
            The email or password is incorrect. Check them and try again.
          </Banner>
        )}
        {result?.status === 'locked' && (
          <Banner tone="warning" title="Too many attempts">
            For your security, wait {result.retryAfterSeconds ?? 60} seconds before trying again.
          </Banner>
        )}
        <div key={shake} className={shake ? 'sm-shake' : undefined} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <TextField
            label="Institutional email"
            type="email"
            icon={AtSign}
            autoComplete="username"
            inputMode="email"
            placeholder={emailPlaceholder}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={errors.email}
            autoFocus
          />
          <PasswordField ref={passwordRef} label="Password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} error={errors.password} />
        </div>
        <div className="sm-auth__row">
          <span className="sm-auth__muted">Password or one-time password</span>
          {onForgotPassword && (
            <button type="button" className="sm-auth__link" onClick={onForgotPassword}>
              Forgot password?
            </button>
          )}
        </div>
        <Button type="submit" size="lg" block loading={busy} iconRight={ArrowRight}>
          Log in
        </Button>
      </form>
      {demoAccounts && demoAccounts.length > 0 && (
        <details className="sm-auth__demo">
          <summary>
            Demo accounts <span className="sm-eyebrow">mock</span>
          </summary>
          <ul>
            {demoAccounts.map((a) => (
              <li key={a.email}>
                <button
                  type="button"
                  onClick={() => {
                    setEmail(a.email);
                    setPassword(a.password);
                    setErrors({});
                    setResult(null);
                  }}
                >
                  <span>{a.label}</span>
                  <span>{a.password}</span>
                </button>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
