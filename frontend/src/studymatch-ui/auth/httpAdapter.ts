import type { AuthAdapter, ChangePasswordResult, LoginResult, ResetPasswordResult, Session, TokenCheck, VerifyEmailResult } from './types';

export interface HttpAuthOptions {
  /** e.g. "/api" or "https://api.example.com". */
  baseUrl: string;
  /** Custom fetch (tests, interceptors). */
  fetch?: typeof fetch;
  /** Extra headers, e.g. a CSRF token. */
  headers?: () => Record<string, string>;
}

/**
 * REST adapter. Expects cookie sessions (httpOnly, Secure, SameSite=Lax), so no token
 * ever touches JavaScript. Endpoints and status codes:
 *
 *  POST /auth/login                 {email,password}  → 200 Session | 401 | 423 {message} (blocked) | 429 {retryAfterSeconds}
 *  POST /auth/logout                                  → 204
 *  GET  /auth/session                                 → 200 Session | 401
 *  POST /auth/password/change       {current?,next}   → 204 | 400 {error}
 *  POST /auth/password/forgot       {email}           → 202 (always)
 *  GET  /auth/password/reset/:token                   → 204 | 404 | 410
 *  POST /auth/password/reset        {token,password}  → 204 | 400 | 404 | 410
 *  POST /auth/email/verification    {personalEmail}   → 202
 *  POST /auth/email/verify          {token}           → 200 {email} | 404 | 410
 *  POST /admin/accounts             {role,name,institutionalEmail}   → 201 {id}
 *  POST /staff/students             {name,studentNumber,…}           → 201 {id,institutionalEmail}
 *  PUT  /admin/accounts/:id/block   {reason} · DELETE to unblock     → 204
 *
 * Create endpoints receive an `Idempotency-Key` header so a double click
 * or a retry never creates the same account twice.
 */
export function createHttpAuthAdapter({ baseUrl, fetch: f = fetch.bind(globalThis), headers }: HttpAuthOptions): AuthAdapter {
  const call = (path: string, init: RequestInit & { json?: unknown; idempotent?: boolean } = {}) => {
    const { json, idempotent, ...rest } = init;
    return f(`${baseUrl.replace(/\/$/, '')}${path}`, {
      credentials: 'include',
      ...rest,
      headers: {
        Accept: 'application/json',
        ...(json !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(idempotent ? { 'Idempotency-Key': crypto.randomUUID() } : {}),
        ...headers?.(),
        ...(rest.headers as Record<string, string> | undefined),
      },
      body: json !== undefined ? JSON.stringify(json) : rest.body,
    });
  };
  const body = async <T>(r: Response): Promise<T> => (r.headers.get('content-type')?.includes('json') ? ((await r.json()) as T) : ({} as T));
  const fail = async (r: Response) => {
    const b = await body<{ message?: string }>(r);
    return new Error(b.message ?? `Request failed (${r.status})`);
  };

  return {
    async login(email, password): Promise<LoginResult> {
      const r = await call('/auth/login', { method: 'POST', json: { email, password } });
      if (r.ok) return { status: 'ok', session: await body<Session>(r) };
      if (r.status === 401) return { status: 'invalid' };
      if (r.status === 423) return { status: 'blocked', message: (await body<{ message?: string }>(r)).message };
      if (r.status === 429) return { status: 'locked', retryAfterSeconds: (await body<{ retryAfterSeconds?: number }>(r)).retryAfterSeconds };
      throw await fail(r);
    },
    async logout() {
      await call('/auth/logout', { method: 'POST' });
    },
    async getSession() {
      const r = await call('/auth/session');
      return r.ok ? body<Session>(r) : null;
    },
    async changePassword(input): Promise<ChangePasswordResult> {
      const r = await call('/auth/password/change', { method: 'POST', json: input });
      if (r.ok) return { ok: true };
      if (r.status === 400) return { ok: false, ...(await body<{ error: 'wrong-current' | 'same-as-current' | 'policy'; message?: string }>(r)) };
      throw await fail(r);
    },
    async requestPasswordReset(email) {
      await call('/auth/password/forgot', { method: 'POST', json: { email } });
    },
    async checkResetToken(token): Promise<TokenCheck> {
      const r = await call(`/auth/password/reset/${encodeURIComponent(token)}`);
      return r.ok ? 'valid' : r.status === 410 ? 'expired' : 'invalid';
    },
    async resetPassword(token, next): Promise<ResetPasswordResult> {
      const r = await call('/auth/password/reset', { method: 'POST', json: { token, password: next } });
      if (r.ok) return { ok: true };
      if (r.status === 410) return { ok: false, error: 'expired' };
      if (r.status === 404) return { ok: false, error: 'invalid' };
      return { ok: false, error: 'policy', message: (await body<{ message?: string }>(r)).message };
    },
    async requestEmailVerification(personalEmail) {
      const r = await call('/auth/email/verification', { method: 'POST', json: { personalEmail } });
      if (!r.ok) throw await fail(r);
    },
    async verifyEmail(token): Promise<VerifyEmailResult> {
      const r = await call('/auth/email/verify', { method: 'POST', json: { token } });
      if (r.ok) return { ok: true, email: (await body<{ email: string }>(r)).email };
      return { ok: false, error: r.status === 410 ? 'expired' : 'invalid' };
    },
    async createAccount(input) {
      const r = await call('/admin/accounts', { method: 'POST', json: input, idempotent: true });
      if (!r.ok) throw await fail(r);
      return body<{ id: string }>(r);
    },
    async registerStudent(input) {
      const r = await call('/staff/students', { method: 'POST', json: input, idempotent: true });
      if (!r.ok) throw await fail(r);
      return body<{ id: string; institutionalEmail: string }>(r);
    },
    async setBlocked(userId, blocked, reason) {
      const r = await call(`/admin/accounts/${encodeURIComponent(userId)}/block`, { method: blocked ? 'PUT' : 'DELETE', json: blocked ? { reason } : undefined });
      if (!r.ok) throw await fail(r);
    },
  };
}
