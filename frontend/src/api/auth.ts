import type { CurrentUser } from '../types/currentUser';
import type { LoginInput } from '../types/forms';
import { readCsrfToken, request } from './client';
import { isBackendError } from './errors';

export async function getCurrentUser(): Promise<CurrentUser | null> {
  try {
    return await request<CurrentUser>('GET', '/users/me');
  } catch (error) {
    if (isBackendError(error) && error.code === 'UNAUTHENTICATED') {
      return null;
    }
    throw error;
  }
}

export async function login(credentials: LoginInput): Promise<CurrentUser> {
  if (readCsrfToken() === undefined) {
    await getCurrentUser();
  }

  return request<CurrentUser>('POST', '/users/login', credentials);
}

export function logout(): Promise<void> {
  return request<void>('POST', '/users/logout');
}
