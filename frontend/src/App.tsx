import { AnimatePresence } from 'framer-motion';
import { useEffect, useState } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { getCurrentUser } from './api/auth';
import { connectChatSocket, disconnectChatSocket } from './api/chatSocket';
import { forgetConversations } from './pages/chats/conversations';
import { LoginPage } from './pages/login/LoginPage';
import { ChangePasswordPage } from './pages/password/ChangePasswordPage';
import { HelpBeforeTest, HelpLink } from './pages/help/HelpBeforeTest';
import { PersonalityTestPage } from './pages/personality/PersonalityTestPage';
import { StudentArea } from './pages/student/StudentArea';
import { AdminArea } from './pages/admin/AdminArea';
import { StaffArea } from './pages/staff/StaffArea';
import { TeacherArea } from './pages/teacher/TeacherArea';
import { VerifyCertificatePage } from './pages/verify/VerifyCertificatePage';
import { LoadingScreen } from './studymatch-ui';
import type { CurrentUser } from './types/currentUser';

export default function App() {
  // a certificate's QR code works for anyone, so it's outside the session
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/verify/:code" element={<VerifyCertificatePage />} />
        <Route path="*" element={<ApplicationSession />} />
      </Routes>
    </BrowserRouter>
  );
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

  const canChat = currentUser !== null && canUseChats(currentUser);
  useEffect(() => {
    if (!canChat) {
      return;
    }
    connectChatSocket();
    return () => { void disconnectChatSocket(); };
  }, [canChat]);

  return (
    <>
      <AnimatePresence>{isCheckingSession && <LoadingScreen key="loading" />}</AnimatePresence>
      {!isCheckingSession && <SessionPage currentUser={currentUser} setCurrentUser={setCurrentUser} />}
    </>
  );
}

// the backend only opens the chat socket for these, after the password change and the personality test
function canUseChats(user: CurrentUser): boolean {
  return (user.role === 'STUDENT' || user.role === 'TEACHER') && !user.mustChangePassword && !user.personalityTestRequired;
}

interface SessionPageProps {
  currentUser: CurrentUser | null;
  setCurrentUser: (user: CurrentUser | null) => void;
}

function SessionPage({ currentUser, setCurrentUser }: SessionPageProps) {
  if (currentUser === null) {
    return <LoginPage onLoggedIn={setCurrentUser} />;
  }

  const onLoggedOut = () => {
    forgetConversations();
    setCurrentUser(null);
  };

  if (currentUser.mustChangePassword) {
    return <ChangePasswordPage onPasswordChanged={setCurrentUser} onLoggedOut={onLoggedOut} />;
  }

  // Help works before the test too (#363)
  if (currentUser.role === 'STUDENT' && currentUser.personalityTestRequired) {
    return (
      <Routes>
        <Route path="/help/*" element={<HelpBeforeTest me={currentUser.number} myName={currentUser.name} />} />
        <Route path="*" element={<PersonalityTestPage help={<HelpLink />} onCompleted={() => setCurrentUser({ ...currentUser, personalityTestRequired: false })} />} />
      </Routes>
    );
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
