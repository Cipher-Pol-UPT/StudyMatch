import { useState } from 'react';
import { ComponentPreview } from './ComponentPreview';
import { LoginPage } from './pages/login/LoginPage';
import type { Session } from './types/session';

export default function App() {
  const [session, setSession] = useState<Session | null>(null);

  if (session === null) {
    return <LoginPage onLoggedIn={setSession} />;
  }

  return <ComponentPreview />;
}
