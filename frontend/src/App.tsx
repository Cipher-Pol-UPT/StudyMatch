import { Brain, House, KeyRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getCurrentUser } from './api/auth';
import { LoginPage } from './pages/login/LoginPage';
import { LogoutButton } from './pages/logout/LogoutButton';
import { PlaceholderPage } from './pages/placeholder/PlaceholderPage';
import type { CurrentUser } from './types/currentUser';

export default function App() {
  const [isCheckingSession, setIsCheckingSession] = useState(true);
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);

  useEffect(() => {
    getCurrentUser()
      .then(setCurrentUser)
      .catch(() => setCurrentUser(null))
      .finally(() => setIsCheckingSession(false));
  }, []);

  if (isCheckingSession) {
    return null;
  }

  if (currentUser === null) {
    return <LoginPage onLoggedIn={setCurrentUser} />;
  }

  return (
    <>
      <LogoutButton onLoggedOut={() => setCurrentUser(null)} />
      <SignedInPage user={currentUser} />
    </>
  );
}

function SignedInPage({ user }: { user: CurrentUser }) {
  const { t } = useTranslation();

  if (user.mustChangePassword) {
    return <PlaceholderPage icon={KeyRound} title={t('auth.changePassword')} />;
  }

  if (user.personalityTestRequired) {
    return <PlaceholderPage icon={Brain} title={t('personality.title')} />;
  }

  return <PlaceholderPage icon={House} title={t('home.greeting', { name: user.name })} />;
}
