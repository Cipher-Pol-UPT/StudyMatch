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

// a form with files, telling how much of it went up (0 to 100); fetch can't, so it's an XMLHttpRequest (#211).
// It answers with the status and the body, and the error is thrown here like in send
export async function uploadWithProgress<T>(path: string, form: FormData, onProgress: (percent: number) => void): Promise<T> {
  const { status, body } = await postWithProgress(path, form, onProgress);
  if (status < 200 || status >= 300) {
    throw isBackendError(body) ? body : new Error(`POST ${path} failed with status ${status}`);
  }
  return body as T;
}

function postWithProgress(path: string, form: FormData, onProgress: (percent: number) => void) {
  return new Promise<{ status: number; body: unknown }>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_PREFIX}${path}`);
    xhr.withCredentials = true;
    const csrfToken = readCsrfToken();
    if (csrfToken !== undefined) xhr.setRequestHeader(CSRF_HEADER, csrfToken);
    xhr.upload.onprogress = event => { if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100)); };
    xhr.onload = () => {
      let body: unknown = null;
      try { body = JSON.parse(xhr.responseText); } catch { /* not JSON, like a proxy's error page */ }
      resolve({ status: xhr.status, body });
    };
    xhr.onerror = () => reject(new Error(`POST ${path} failed`));
    xhr.send(form);
  });
}

// with a body it's a POST, for files the backend builds from what was sent
export async function download(path: string, body?: unknown): Promise<Blob> {
  const response = body === undefined
    ? await send('GET', path)
    : await send('POST', path, { contentType: 'application/json', body: JSON.stringify(body) });
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
