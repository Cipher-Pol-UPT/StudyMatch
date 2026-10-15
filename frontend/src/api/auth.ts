import type { LoginInput } from '../types/forms';
import type { Session } from '../types/session';
import { isBackendError } from './errors';

export type LoginResult =
  | { outcome: 'loggedIn'; session: Session }
  | { outcome: 'invalidCredentials' }
  | { outcome: 'accountBlocked' };

const UNAUTHORIZED = 401;
const FORBIDDEN = 403;

export async function login(credentials: LoginInput): Promise<LoginResult> {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(credentials),
  });

  if (response.status === UNAUTHORIZED) {
    return { outcome: 'invalidCredentials' };
  }

  if (response.status === FORBIDDEN) {
    return { outcome: 'accountBlocked' };
  }

  if (!response.ok) {
    const error: unknown = await response.json().catch(() => null);
    throw isBackendError(error) ? error : new Error(`Login failed with status ${response.status}`);
  }

  const session: Session = await response.json();
  return { outcome: 'loggedIn', session };
}
