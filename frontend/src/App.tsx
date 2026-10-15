import { AnimatePresence, MotionConfig } from 'framer-motion';
import { useEffect, useState } from 'react';
import { ComponentPreview } from './ComponentPreview';
import { LoadingScreen } from './components/loading/LoadingScreen';
import { LoginPage } from './pages/login/LoginPage';
import type { Session } from './types/session';

const LOADING_SCREEN_DURATION_MS = 1200;

export default function App() {
  const [isLoading, setIsLoading] = useState(true);
  const [session, setSession] = useState<Session | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => setIsLoading(false), LOADING_SCREEN_DURATION_MS);
    return () => clearTimeout(timer);
  }, []);

  function renderCurrentScreen() {
    if (isLoading) {
      return <LoadingScreen key="loading" />;
    }

    if (session === null) {
      return <LoginPage key="login" onLoggedIn={setSession} />;
    }

    return <ComponentPreview key="home" />;
  }

  return (
    <MotionConfig reducedMotion="user">
      <AnimatePresence mode="wait">
        {renderCurrentScreen()}
      </AnimatePresence>
    </MotionConfig>
  );
}
