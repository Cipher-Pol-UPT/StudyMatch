import { isBackendError } from './errors';

const API_PREFIX = '/api';
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const response = await send(method, path, body === undefined ? undefined : {
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json();
}

export async function upload<T>(path: string, form: FormData): Promise<T> {
  const response = await send('POST', path, { body: form });
  return response.json();
}

export async function download(path: string): Promise<Blob> {
  const response = await send('GET', path);
  return response.blob();
}

async function send(method: Method, path: string, payload?: { contentType?: string; body: BodyInit }): Promise<Response> {
  const headers: Record<string, string> = {};
  const csrfToken = readCsrfToken();

  if (payload?.contentType !== undefined) {
    headers['Content-Type'] = payload.contentType;
  }

  if (method !== 'GET' && csrfToken !== undefined) {
    headers[CSRF_HEADER] = csrfToken;
  }

  const response = await fetch(`${API_PREFIX}${path}`, {
    method,
    headers,
    credentials: 'include',
    body: payload?.body,
  });

  if (!response.ok) {
    const error: unknown = await response.json().catch(() => null);
    throw isBackendError(error) ? error : new Error(`${method} ${path} failed with status ${response.status}`);
  }

  return response;
}

export function readCsrfToken(): string | undefined {
  const cookie = document.cookie
    .split('; ')
    .find(entry => entry.startsWith(`${CSRF_COOKIE}=`));

  return cookie === undefined ? undefined : decodeURIComponent(cookie.slice(CSRF_COOKIE.length + 1));
}
