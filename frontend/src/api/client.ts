import { isBackendError } from './errors';

const API_PREFIX = '/api';
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const csrfToken = readCsrfToken();

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  if (method !== 'GET' && csrfToken !== undefined) {
    headers[CSRF_HEADER] = csrfToken;
  }

  const response = await fetch(`${API_PREFIX}${path}`, {
    method,
    headers,
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (!response.ok) {
    const error: unknown = await response.json().catch(() => null);
    throw isBackendError(error) ? error : new Error(`${method} ${path} failed with status ${response.status}`);
  }

  return response.json();
}

export function readCsrfToken(): string | undefined {
  const cookie = document.cookie
    .split('; ')
    .find(entry => entry.startsWith(`${CSRF_COOKIE}=`));

  return cookie === undefined ? undefined : decodeURIComponent(cookie.slice(CSRF_COOKIE.length + 1));
}
