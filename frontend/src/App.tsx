import { useEffect, useState } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { getCurrentUser } from './api/auth';
import { LoginPage } from './pages/login/LoginPage';
import { ChangePasswordPage } from './pages/password/ChangePasswordPage';
import { PersonalityTestPage } from './pages/personality/PersonalityTestPage';
import { StudentArea } from './pages/student/StudentArea';
import { WorkspaceArea } from './pages/workspace/WorkspaceArea';
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

  const logOut = () => setCurrentUser(null);

  if (currentUser.mustChangePassword) {
    return <ChangePasswordPage onPasswordChanged={setCurrentUser} onLoggedOut={logOut} />;
  }

  if (currentUser.personalityTestRequired) {
    return <PersonalityTestPage onCompleted={() => setCurrentUser({ ...currentUser, personalityTestRequired: false })} />;
  }

  return (
    <BrowserRouter>
      {currentUser.role === 'STUDENT'
        ? <StudentArea currentUser={currentUser} onLoggedOut={logOut} />
        : <WorkspaceArea currentUser={currentUser} onLoggedOut={logOut} />}
    </BrowserRouter>
  );
}
