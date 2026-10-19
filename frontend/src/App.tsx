import { House, KeyRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getCurrentUser } from './api/auth';
import { LoginPage } from './pages/login/LoginPage';
import { PersonalityTestPage } from './pages/personality/PersonalityTestPage';
import { PlaceholderPage } from './pages/placeholder/PlaceholderPage';
import type { CurrentUser } from './types/currentUser';

export default function App() {
  const { t } = useTranslation();
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

  if (currentUser.mustChangePassword) {
    return <PlaceholderPage icon={KeyRound} title={t('auth.changePassword')} />;
  }

  if (currentUser.personalityTestRequired) {
    return <PersonalityTestPage onCompleted={() => setCurrentUser({ ...currentUser, personalityTestRequired: false })} />;
  }

  return <PlaceholderPage icon={House} title={t('home.greeting', { name: currentUser.name })} />;
}
