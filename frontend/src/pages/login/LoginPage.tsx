import { MotionConfig, motion } from 'framer-motion';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { login } from '../../api/auth';
import { isBackendError } from '../../api/errors';
import { AuthLayout, BlockedNotice, LoginForm, useToast } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import type { LoginInput } from '../../types/forms';
import { ForgotPassword } from './ForgotPassword';
import { RevealText } from './RevealText';

export interface LoginPageProps {
  onLoggedIn: (user: CurrentUser) => void;
}

type LoginView = 'login' | 'accountBlocked' | 'forgotPassword';

export function LoginPage({ onLoggedIn }: LoginPageProps) {
  const { t } = useTranslation();
  const toast = useToast();
  const [view, setView] = useState<LoginView>('login');

  async function handleLogin(credentials: LoginInput) {
    try {
      onLoggedIn(await login(credentials));
    } catch (error) {
      if (isBackendError(error) && error.code === 'ACCOUNT_BLOCKED') {
        setView('accountBlocked');
        return;
      }
      throw error;
    }
  }

  function showLoginAfterPasswordReset() {
    setView('login');
    toast.success(t('auth.passwordReset'));
  }

  return (
    <MotionConfig reducedMotion="user">
      <AuthLayout centerBrand hideArt hideFooter headline={<RevealText text={t('auth.headline')} />}>
        <FadeIn key={view}>
          {view === 'accountBlocked' && (
            <BlockedNotice message={<p>{t('errors.ACCOUNT_BLOCKED')}</p>} onBack={() => setView('login')} />
          )}
          {view === 'forgotPassword' && (
            <ForgotPassword onPasswordReset={showLoginAfterPasswordReset} onBack={() => setView('login')} />
          )}
          {view === 'login' && (
            <LoginForm onSubmit={handleLogin} onForgotPassword={() => setView('forgotPassword')} />
          )}
        </FadeIn>
      </AuthLayout>
    </MotionConfig>
  );
}

function FadeIn({ children }: { children: ReactNode }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, delay: 0.15, ease: [0.22, 1, 0.36, 1] }}
    >
      {children}
    </motion.div>
  );
}
