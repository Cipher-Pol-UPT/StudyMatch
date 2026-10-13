import { CheckCircle2, Lock, MailCheck, MailWarning, ShieldAlert } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Button } from '../../components/button/Button';
import { Spinner } from '../../components/progress/Progress';
import { useOptionalAuth } from '../../auth/AuthProvider';
import type { IconComponent } from '../../utils/shared';
import './auth.css';

const TONES = {
  violet: ['var(--sm-tint-lavender)', 'var(--sm-violet)'],
  success: ['var(--sm-success-50)', 'var(--sm-success)'],
  danger: ['var(--sm-danger-50)', 'var(--sm-danger)'],
  warning: ['var(--sm-warning-50)', 'var(--sm-warning-ink)'],
} as const;

export interface AuthStatusProps {
  icon: IconComponent;
  tone?: keyof typeof TONES;
  title: ReactNode;
  children?: ReactNode;
  actions?: ReactNode;
  /** Hide the spark dot. */
  plain?: boolean;
}

/** Shared layout for confirmation and error screens inside AuthLayout. */
export function AuthStatus({ icon: Icon, tone = 'violet', title, children, actions, plain }: AuthStatusProps) {
  const [bg, fg] = TONES[tone];
  return (
    <div className="sm-auth__status" role="status">
      <div className={`sm-auth__status-art${plain ? ' sm-auth__status-art--plain' : ''}`} style={{ background: bg, color: fg }}>
        <Icon size={40} strokeWidth={1.75} aria-hidden />
      </div>
      <h2 className="sm-auth__title">{title}</h2>
      {children && <div className="sm-auth__subtitle">{children}</div>}
      {actions && <div className="sm-auth__actions">{actions}</div>}
    </div>
  );
}

// ─── Check your inbox ──────────────────────────────────────
export interface CheckEmailProps {
  email: string;
  title?: ReactNode;
  description?: ReactNode;
  /** Resend the email. A cooldown prevents spamming the SMTP server. */
  onResend?: () => Promise<void> | void;
  cooldownSeconds?: number;
  onBack?: () => void;
  backLabel?: string;
}

export function CheckEmail({ email, title = 'Check your inbox', description, onResend, cooldownSeconds = 30, onBack, backLabel = 'Back to log in' }: CheckEmailProps) {
  const [left, setLeft] = useState(cooldownSeconds);
  const [sending, setSending] = useState(false);
  useEffect(() => {
    if (left <= 0) return;
    const t = window.setTimeout(() => setLeft((s) => s - 1), 1000);
    return () => window.clearTimeout(t);
  }, [left]);
  return (
    <AuthStatus
      icon={MailCheck}
      title={title}
      actions={
        <>
          {onResend && (
            <Button
              variant="secondary"
              size="lg"
              block
              loading={sending}
              disabled={left > 0}
              onClick={async () => {
                setSending(true);
                try {
                  await onResend();
                  setLeft(cooldownSeconds);
                } finally {
                  setSending(false);
                }
              }}
            >
              {left > 0 ? `Resend in ${left}s` : 'Resend email'}
            </Button>
          )}
          {onBack && (
            <Button variant="ghost" size="lg" block onClick={onBack}>
              {backLabel}
            </Button>
          )}
        </>
      }
    >
      {description ?? 'We sent a link to the address below. It can take a minute to arrive; check your spam folder too.'}
      <div className="sm-auth__email-pill">{email}</div>
    </AuthStatus>
  );
}

// ─── Blocked account ───────────────────────────────────────
export interface BlockedNoticeProps {
  message?: ReactNode;
  /** How to reach academic services. */
  contact?: { label: string; href: string };
  onBack?: () => void;
}

export function BlockedNotice({ message, contact = { label: 'Contact academic services', href: 'mailto:academic.services@uni.example' }, onBack }: BlockedNoticeProps) {
  return (
    <AuthStatus
      icon={Lock}
      tone="danger"
      plain
      title="Your account is blocked"
      actions={
        <>
          <a className="sm-btn sm-btn--black sm-btn--lg sm-btn--block" href={contact.href}>
            <span className="sm-btn__label">{contact.label}</span>
          </a>
          {onBack && (
            <Button variant="ghost" size="lg" block onClick={onBack}>
              Use another account
            </Button>
          )}
        </>
      }
    >
      {message ?? 'You can’t log in right now. Academic services can tell you why and help you restore access. Your groups and academic history are kept.'}
    </AuthStatus>
  );
}

// ─── Verify email (landing page of the link) ───────────────
export interface VerifyEmailProps {
  token: string | null;
  /** Called with the confirmed address. */
  onVerified?: (email: string) => void;
  onContinue?: () => void;
  /** Ask for a new link (only possible while signed in). */
  onRequestNewLink?: () => void;
  /** Custom verification instead of the adapter. */
  verify?: (token: string) => Promise<{ ok: true; email: string } | { ok: false; error: 'expired' | 'invalid' }>;
}

/** Opens from the email link: verifies the token and reports the result. */
export function VerifyEmail({ token, onVerified, onContinue, onRequestNewLink, verify }: VerifyEmailProps) {
  const auth = useOptionalAuth();
  const [state, setState] = useState<{ s: 'loading' } | { s: 'ok'; email: string } | { s: 'expired' } | { s: 'invalid' }>({ s: 'loading' });
  // Tokens are single-use: never send the same one twice (StrictMode re-runs effects in development).
  const request = useRef<{ token: string; promise: ReturnType<NonNullable<VerifyEmailProps['verify']>> } | null>(null);
  useEffect(() => {
    let alive = true;
    const run = verify ?? auth?.adapter.verifyEmail.bind(auth.adapter);
    if (!token || !run) {
      setState({ s: 'invalid' });
      return;
    }
    if (request.current?.token !== token) request.current = { token, promise: run(token) };
    request.current.promise.then((r) => {
      if (!alive) return;
      if (r.ok) {
        setState({ s: 'ok', email: r.email });
        auth?.updateUser({ personalEmail: r.email, personalEmailVerified: true });
        onVerified?.(r.email);
      } else setState({ s: r.error });
    });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  if (state.s === 'loading')
    return (
      <div className="sm-auth__status" role="status" aria-live="polite">
        <Spinner size={40} label="Verifying your email" />
        <h2 className="sm-auth__title" style={{ marginTop: 24 }}>
          Confirming your email…
        </h2>
      </div>
    );
  if (state.s === 'ok')
    return (
      <AuthStatus icon={CheckCircle2} tone="success" title="Email confirmed" actions={onContinue && <Button size="lg" block onClick={onContinue}>Continue</Button>}>
        <b style={{ color: 'var(--sm-black)', fontWeight: 500 }}>{state.email}</b> is now linked to your account. We’ll use it for account recovery and important notices.
      </AuthStatus>
    );
  return (
    <AuthStatus
      icon={state.s === 'expired' ? MailWarning : ShieldAlert}
      tone="warning"
      plain
      title={state.s === 'expired' ? 'This link has expired' : 'This link isn’t valid'}
      actions={
        <>
          {onRequestNewLink && (
            <Button size="lg" block onClick={onRequestNewLink}>
              Send a new link
            </Button>
          )}
          {onContinue && (
            <Button variant="ghost" size="lg" block onClick={onContinue}>
              Back to log in
            </Button>
          )}
        </>
      }
    >
      {state.s === 'expired' ? 'Verification links last 24 hours. Request a new one and use the most recent email.' : 'It may have been used already, or copied incompletely. Request a new link to continue.'}
    </AuthStatus>
  );
}
