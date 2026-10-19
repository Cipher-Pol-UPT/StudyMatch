import { KeyRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { BrowserRouter } from 'react-router-dom';
import { getCurrentUser } from './api/auth';
import { LoginPage } from './pages/login/LoginPage';
import { PersonalityTestPage } from './pages/personality/PersonalityTestPage';
import { PlaceholderPage } from './pages/placeholder/PlaceholderPage';
import { StudentArea } from './pages/student/StudentArea';
import { AdminArea } from './pages/admin/AdminArea';
import { StaffArea } from './pages/staff/StaffArea';
import { TeacherArea } from './pages/teacher/TeacherArea';
import type { CurrentUser } from './types/currentUser';

export default function App() {
  return <BrowserRouter><ApplicationSession /></BrowserRouter>;
}

function ApplicationSession() {
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

  if (currentUser.role === 'STUDENT' && currentUser.personalityTestRequired) {
    return <PersonalityTestPage onCompleted={() => setCurrentUser({ ...currentUser, personalityTestRequired: false })} />;
  }

  const onLoggedOut = () => setCurrentUser(null);

  switch (currentUser.role) {
    case 'ADMIN':
      return <AdminArea key={currentUser.number} currentUser={currentUser} onLoggedOut={onLoggedOut} />;
    case 'STAFF':
      return <StaffArea key={currentUser.number} currentUser={currentUser} onLoggedOut={onLoggedOut} />;
    case 'TEACHER':
      return <TeacherArea key={currentUser.number} currentUser={currentUser} onLoggedOut={onLoggedOut} />;
    case 'STUDENT':
      return <StudentArea key={currentUser.number} currentUser={currentUser} onLoggedOut={onLoggedOut} />;
  }
}
