import { useEffect, useState } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { getCurrentUser } from './api/auth';
import { LoginPage } from './pages/login/LoginPage';
import { ChangePasswordPage } from './pages/password/ChangePasswordPage';
import { PersonalityTestPage } from './pages/personality/PersonalityTestPage';
import { StudentArea } from './pages/student/StudentArea';
import { AdminArea } from './pages/admin/AdminArea';
import { StaffArea } from './pages/staff/StaffArea';
import { TeacherArea } from './pages/teacher/TeacherArea';
import type { CurrentUser } from './types/currentUser';

export default function App() {
  return <BrowserRouter><ApplicationSession /></BrowserRouter>;
}

function ApplicationSession() {
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

  const onLoggedOut = () => setCurrentUser(null);

  if (currentUser.mustChangePassword) {
    return <ChangePasswordPage onPasswordChanged={setCurrentUser} onLoggedOut={onLoggedOut} />;
  }

  if (currentUser.role === 'STUDENT' && currentUser.personalityTestRequired) {
    return <PersonalityTestPage onCompleted={() => setCurrentUser({ ...currentUser, personalityTestRequired: false })} />;
  }

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
