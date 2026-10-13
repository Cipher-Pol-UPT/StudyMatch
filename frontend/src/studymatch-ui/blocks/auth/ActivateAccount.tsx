import { AtSign, ShieldCheck } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useOptionalAuth } from '../../auth/AuthProvider';
import { Button } from '../../components/button/Button';
import { TextField } from '../../components/field/TextField';
import { Banner } from '../../components/overlay/Banner';
import { Steps } from '../../components/steps/Steps';
import { AuthHeader } from './AuthLayout';
import { isEmail } from './LoginForm';
import { ChangePasswordForm } from './PasswordForms';
import { CheckEmail } from './StatusScreens';
import './auth.css';

export interface ActivateAccountProps {
  /** Called when the account is fully set up. */
  onDone: () => void;
  /** Ask for (and verify) a personal email. Recommended: it is the recovery channel. */
  requirePersonalEmail?: boolean;
  /** Start at a later step (e.g. the password is set but the email is not verified). */
  startAt?: 'password' | 'email';
  onLogout?: () => void;
}

/**
 * First login: the account came from the university records (or was created by Staff/Admin)
 * with a one-time password. 1) choose a password, 2) add a personal email, 3) confirm it.
 */
export function ActivateAccount({ onDone, requirePersonalEmail = true, startAt, onLogout }: ActivateAccountProps) {
  const auth = useOptionalAuth();
  const initial = startAt ?? (auth?.session?.mustChangePassword === false ? 'email' : 'password');
  const [step, setStep] = useState<0 | 1 | 2>(initial === 'email' ? 1 : 0);
  const [email, setEmail] = useState(auth?.user?.personalEmail ?? '');
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [notYet, setNotYet] = useState(false);

  const sendLink = async () => {
    if (!auth) throw new Error('ActivateAccount needs <AuthProvider>.');
    await auth.adapter.requestEmailVerification(email.trim());
  };

  const submitEmail = async (e: FormEvent) => {
    e.preventDefault();
    if (!isEmail(email)) {
      setError('Enter a valid personal email.');
      return;
    }
    if (auth?.user && email.trim().toLowerCase() === auth.user.institutionalEmail.toLowerCase()) {
      setError('Use a personal address, not your institutional email.');
      return;
    }
    setBusy(true);
    try {
      await sendLink();
      setStep(2);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Couldn’t send the email. Try again.');
    } finally {
      setBusy(false);
    }
  };

  const steps = requirePersonalEmail ? ['Password', 'Personal email', 'Confirm'] : ['Password'];

  return (
    <div>
      {requirePersonalEmail && <Steps steps={steps} current={step} className="sm-activate__steps" />}
      <div style={{ marginTop: requirePersonalEmail ? 28 : 0 }}>
        {step === 0 && (
          <ChangePasswordForm
            mode="first-login"
            title="Welcome to StudyMatch"
            subtitle="Your account was created from the university records. First, choose your own password; the one-time password stops working."
            onSuccess={() => (requirePersonalEmail ? setStep(1) : onDone())}
          />
        )}
        {step === 1 && (
          <div>
            <AuthHeader title="Add your personal email" subtitle="We’ll use it to recover your account and for important notices. We’ll send a link to confirm it’s yours." />
            <form className="sm-auth__form" onSubmit={submitEmail} noValidate>
              <TextField
                label="Personal email"
                type="email"
                icon={AtSign}
                autoComplete="email"
                placeholder="name@mail.example"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  setError(undefined);
                }}
                error={error}
                hint="Not your institutional address."
                autoFocus
              />
              <Button type="submit" size="lg" block loading={busy}>
                Send confirmation link
              </Button>
            </form>
          </div>
        )}
        {step === 2 && (
          <div>
            <CheckEmail
              email={email.trim()}
              title="Confirm your email"
              description="Open the link we just sent to finish activating your account. It expires in 24 hours."
              onResend={sendLink}
              onBack={() => setStep(1)}
              backLabel="Use a different email"
            />
            {notYet && (
              <div style={{ marginTop: 16 }}>
                <Banner tone="warning">We haven’t seen the confirmation yet. Open the link in the email, then try again.</Banner>
              </div>
            )}
            <div className="sm-auth__actions" style={{ marginTop: 10 }}>
              <Button
                size="lg"
                block
                icon={ShieldCheck}
                loading={busy}
                onClick={async () => {
                  if (!auth) return onDone();
                  setBusy(true);
                  const s = await auth.adapter.getSession().catch(() => null);
                  setBusy(false);
                  if (s?.user.personalEmailVerified) {
                    auth.updateUser({ personalEmail: s.user.personalEmail, personalEmailVerified: true });
                    onDone();
                  } else setNotYet(true);
                }}
              >
                I’ve confirmed it
              </Button>
            </div>
          </div>
        )}
        {onLogout && (
          <p className="sm-auth__muted" style={{ marginTop: 24, textAlign: 'center' }}>
            Not you?{' '}
            <button type="button" className="sm-auth__link" onClick={onLogout}>
              Log out
            </button>
          </p>
        )}
      </div>
    </div>
  );
}
