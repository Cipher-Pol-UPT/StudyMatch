import { MotionConfig, motion } from 'framer-motion';
import { useState, type PointerEvent, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { BackendError } from '../../api/errors';
import { login } from '../../api/auth';
import { AuthLayout, Banner, BlockedNotice, LoginForm } from '../../studymatch-ui';
import type { LoginInput } from '../../types/forms';
import type { Session } from '../../types/session';
import { RevealText } from './RevealText';
import { validateLogin } from './validateLogin';
import './LoginPage.css';

export interface LoginPageProps {
  onLoggedIn: (session: Session) => void;
}

export function LoginPage({ onLoggedIn }: LoginPageProps) {
  const { t } = useTranslation();
  const [credentialsRejected, setCredentialsRejected] = useState(false);
  const [accountBlocked, setAccountBlocked] = useState(false);

  async function handleLogin(input: LoginInput) {
    setCredentialsRejected(false);

    const credentials = { email: input.email.trim(), password: input.password };
    const fieldErrors = validateLogin(credentials);

    if (fieldErrors.length > 0) {
      const validationError: BackendError = { code: 'VALIDATION_FAILED', fieldErrors };
      throw validationError;
    }

    const result = await login(credentials);

    if (result.outcome === 'invalidCredentials') {
      setCredentialsRejected(true);
    } else if (result.outcome === 'accountBlocked') {
      setAccountBlocked(true);
    } else {
      onLoggedIn(result.session);
    }
  }

  return (
    <MotionConfig reducedMotion="user">
      <div className="login-page__pointer-area" onPointerMove={moveDotsWithPointer} onPointerLeave={resetDots}>
        <AuthLayout
          className="login-page"
          headline={<RevealText text={t('ui.better_teams_start_here')} />}
          tagline={t('ui.studymatch_forms_balanced_groups_from_your_strengths_grades_and_a')}
          art={null}
          footer={null}
        >
          <FadeIn key={accountBlocked ? 'blocked' : 'login'} delay={0.15}>
            {accountBlocked ? (
              <BlockedNotice message={<p>{t('auth.accountBlocked')}</p>} onBack={() => setAccountBlocked(false)} />
            ) : (
              <>
                {credentialsRejected && <Banner tone="danger">{t('auth.invalidCredentials')}</Banner>}
                <LoginForm onSubmit={handleLogin} />
              </>
            )}
          </FadeIn>
        </AuthLayout>
      </div>
    </MotionConfig>
  );
}

function moveDotsWithPointer(event: PointerEvent<HTMLDivElement>) {
  const brandPanel = event.currentTarget.querySelector('.sm-auth__brand');
  if (brandPanel === null) return;

  const area = brandPanel.getBoundingClientRect();
  const x = (event.clientX - area.left) / area.width - 0.5;
  const y = (event.clientY - area.top) / area.height - 0.5;
  const isOverBrandPanel = Math.abs(x) <= 0.5 && Math.abs(y) <= 0.5;

  event.currentTarget.style.setProperty('--pointer-x', isOverBrandPanel ? x.toFixed(3) : '0');
  event.currentTarget.style.setProperty('--pointer-y', isOverBrandPanel ? y.toFixed(3) : '0');
}

function resetDots(event: PointerEvent<HTMLDivElement>) {
  event.currentTarget.style.setProperty('--pointer-x', '0');
  event.currentTarget.style.setProperty('--pointer-y', '0');
}

function FadeIn({ children, delay = 0 }: { children: ReactNode; delay?: number }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, delay, ease: [0.22, 1, 0.36, 1] }}
    >
      {children}
    </motion.div>
  );
}
