import { Brain, KeyRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getCurrentUser } from './api/auth';
import { LoginPage } from './pages/login/LoginPage';
import { PlaceholderPage } from './pages/placeholder/PlaceholderPage';
import { StudentHome } from './pages/studentHome';
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
    return <PlaceholderPage icon={Brain} title={t('personality.title')} />;
  }

  return <StudentHome userName={currentUser.name} />;
}
