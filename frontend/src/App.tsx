import { Brain, House, KeyRound, UserRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getCurrentUser } from './api/auth';
import { LoginPage } from './pages/login/LoginPage';
import { PlaceholderPage } from './pages/placeholder/PlaceholderPage';
import { ProfilePage } from './pages/profile/ProfilePage';
import { buttonClass } from './studymatch-ui';
import type { CurrentUser } from './types/currentUser';

const PROFILE_PATH = '/profile';

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

  const isStudent = currentUser.role === 'STUDENT';

  if (isStudent && window.location.pathname === PROFILE_PATH) {
    return <ProfilePage />;
  }

  const myProfileLink = (
    <a className={buttonClass({ variant: 'secondary' })} href={PROFILE_PATH}>
      <UserRound size={18} aria-hidden />
      {t('profile.myProfile')}
    </a>
  );

  return (
    <PlaceholderPage
      icon={House}
      title={t('home.greeting', { name: currentUser.name })}
      action={isStudent ? myProfileLink : undefined}
    />
  );
}
