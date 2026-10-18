import { MotionConfig, motion } from 'framer-motion';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { login } from '../../api/auth';
import { isBackendError } from '../../api/errors';
import { AuthLayout, BlockedNotice, LoginForm } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import type { LoginInput } from '../../types/forms';
import { RevealText } from './RevealText';

export interface LoginPageProps {
  onLoggedIn: (user: CurrentUser) => void;
}

export function LoginPage({ onLoggedIn }: LoginPageProps) {
  const { t } = useTranslation();
  const [accountBlocked, setAccountBlocked] = useState(false);

  async function handleLogin(credentials: LoginInput) {
    try {
      onLoggedIn(await login(credentials));
    } catch (error) {
      if (isBackendError(error) && error.code === 'ACCOUNT_BLOCKED') {
        setAccountBlocked(true);
        return;
      }
      throw error;
    }
  }

  return (
    <MotionConfig reducedMotion="user">
      <AuthLayout centerBrand hideArt hideFooter headline={<RevealText text={t('auth.headline')} />}>
        <FadeIn key={accountBlocked ? 'blocked' : 'login'}>
          {accountBlocked ? (
            <BlockedNotice message={<p>{t('errors.ACCOUNT_BLOCKED')}</p>} onBack={() => setAccountBlocked(false)} />
          ) : (
            <LoginForm onSubmit={handleLogin} />
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
