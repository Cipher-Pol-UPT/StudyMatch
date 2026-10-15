import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { BackendError } from '../../api/errors';
import { login } from '../../api/auth';
import { AuthLayout, Banner, BlockedNotice, LoginForm } from '../../studymatch-ui';
import type { LoginInput } from '../../types/forms';
import type { Session } from '../../types/session';
import { validateLogin } from './validateLogin';

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

  if (accountBlocked) {
    return (
      <AuthLayout>
        <BlockedNotice message={<p>{t('auth.accountBlocked')}</p>} onBack={() => setAccountBlocked(false)} />
      </AuthLayout>
    );
  }

  return (
    <AuthLayout>
      {credentialsRejected && <Banner tone="danger">{t('auth.invalidCredentials')}</Banner>}
      <LoginForm onSubmit={handleLogin} />
    </AuthLayout>
  );
}
