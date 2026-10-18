import type { CurrentUser } from '../types/currentUser';
import type { LoginInput, ResetPasswordRequest } from '../types/forms';
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
  await ensureCsrfToken();
  return request<CurrentUser>('POST', '/users/login', credentials);
}

export async function requestPasswordReset(email: string): Promise<void> {
  await ensureCsrfToken();
  return request<void>('POST', '/users/forgot-password', { email });
}

export async function resetPassword(reset: ResetPasswordRequest): Promise<void> {
  await ensureCsrfToken();
  return request<void>('POST', '/users/reset-password', reset);
}

async function ensureCsrfToken() {
  if (readCsrfToken() === undefined) {
    await getCurrentUser();
  }
}
