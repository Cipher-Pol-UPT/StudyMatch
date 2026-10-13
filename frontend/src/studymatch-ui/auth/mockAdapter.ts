import { isPasswordValid } from '../utils/password';
import type { AccountStatus, AuthAdapter, AuthUser, Session } from './types';

/**
 * In-browser auth backend for prototypes, demos and tests.
 * Seeded users, one-time passwords, reset / verification tokens and an "outbox"
 * that collects the emails a real SMTP server would send (like Mailpit in development).
 *
 * ⚠️ Never ship this to production: passwords are stored in plain text in localStorage.
 */

export interface MockUser extends AuthUser {
  password: string;
  status: AccountStatus;
  /** One-time password set when the account is created; valid for a single login. */
  oneTimePassword?: string;
  oneTimePasswordExpiresAt?: string;
  blockedReason?: string;
}

export interface MockEmail {
  id: string;
  to: string;
  subject: string;
  body: string;
  link?: { label: string; href: string };
  /** Shown as a code block, e.g. a one-time password. */
  code?: string;
  sentAt: string;
}

interface MockToken {
  token: string;
  type: 'reset' | 'verify';
  userId: string;
  email: string;
  expiresAt: string;
}

interface MockState {
  users: MockUser[];
  outbox: MockEmail[];
  tokens: MockToken[];
  attempts: Record<string, { count: number; until?: number }>;
  session: { userId: string; mustChangePassword: boolean } | null;
}

export interface MockAuthOptions {
  users?: MockUser[];
  /** Simulated network latency in ms. */
  latency?: number;
  /** Persist state in localStorage so refreshes keep the session. */
  persist?: boolean;
  storageKey?: string;
  /** Origin used in email links. Defaults to window.location.origin. */
  linkBase?: string;
  routes?: { resetPassword?: (token: string) => string; verifyEmail?: (token: string) => string };
  /** Domain for generated student emails: `<number>@<domain>`. */
  studentDomain?: string;
  /** Failed logins before a temporary lock. */
  maxAttempts?: number;
}

export interface MockAuthAdapter extends AuthAdapter {
  /** Emails "sent" so far, newest first. */
  outbox(): MockEmail[];
  clearOutbox(): void;
  users(): MockUser[];
  /** Listen to any state change (new email, login…). Returns an unsubscribe function. */
  subscribe(listener: () => void): () => void;
  /** Restore the seeded users and clear everything. */
  reset(): void;
}

const iso = (msFromNow: number) => new Date(Date.now() + msFromNow).toISOString();
const DAY = 86_400_000;

/** Demo accounts. Emails use the reserved `.example` domain. */
export const MOCK_USERS: MockUser[] = [
  { id: 'u-ana', name: 'Ana Silva', role: 'student', studentNumber: '2410231', institutionalEmail: '2410231@students.uni.example', personalEmail: 'ana.silva@mail.example', personalEmailVerified: true, hasPersonalityTest: true, isTutor: true, password: 'Study2026', status: 'active' },
  { id: 'u-joao', name: 'João Costa', role: 'student', studentNumber: '2410544', institutionalEmail: '2410544@students.uni.example', personalEmail: 'joao.costa@mail.example', personalEmailVerified: false, hasPersonalityTest: false, password: '', oneTimePassword: 'K7Q2-9XMP', oneTimePasswordExpiresAt: iso(7 * DAY), status: 'pending' },
  { id: 'u-rita', name: 'Rita Mendes', role: 'student', studentNumber: '2410872', institutionalEmail: '2410872@students.uni.example', personalEmail: 'rita.mendes@mail.example', personalEmailVerified: true, hasPersonalityTest: false, password: 'Study2026', status: 'active' },
  { id: 'u-tiago', name: 'Tiago Rocha', role: 'student', studentNumber: '2310119', institutionalEmail: '2310119@students.uni.example', personalEmail: 'tiago.rocha@mail.example', personalEmailVerified: true, hasPersonalityTest: true, password: 'Study2026', status: 'blocked', blockedReason: 'Enrolment suspended' },
  { id: 'u-marta', name: 'Marta Ferreira', role: 'teacher', institutionalEmail: 'marta.ferreira@uni.example', personalEmailVerified: true, password: 'Teach2026', status: 'active' },
  { id: 'u-paulo', name: 'Paulo Nunes', role: 'staff', institutionalEmail: 'academic.services@uni.example', personalEmailVerified: true, password: 'Staff2026', status: 'active' },
  { id: 'u-admin', name: 'Inês Barros', role: 'admin', institutionalEmail: 'admin@uni.example', personalEmailVerified: true, password: 'Admin2026', status: 'active' },
];

const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
function randomString(len: number) {
  const bytes = new Uint8Array(len);
  (globalThis.crypto ?? { getRandomValues: (b: Uint8Array) => b.map(() => Math.floor(Math.random() * 256)) }).getRandomValues(bytes);
  return Array.from(bytes, (b) => ALPHABET[b % ALPHABET.length]).join('');
}
const otp = () => `${randomString(4)}-${randomString(4)}`;
const token = () => randomString(32);

const publicUser = ({ password: _p, oneTimePassword: _o, oneTimePasswordExpiresAt: _e, blockedReason: _b, status: _s, ...u }: MockUser): AuthUser => u;

export function createMockAuthAdapter(options: MockAuthOptions = {}): MockAuthAdapter {
  const {
    users: seed = MOCK_USERS,
    latency = 550,
    persist = true,
    storageKey = 'studymatch-mock-auth-v1',
    studentDomain = 'students.uni.example',
    maxAttempts = 5,
  } = options;
  const routes = {
    resetPassword: options.routes?.resetPassword ?? ((t: string) => `/reset-password?token=${t}`),
    verifyEmail: options.routes?.verifyEmail ?? ((t: string) => `/verify-email?token=${t}`),
  };
  const base = () => options.linkBase ?? (typeof window !== 'undefined' ? window.location.origin : '');

  const fresh = (): MockState => ({ users: structuredClone(seed), outbox: [], tokens: [], attempts: {}, session: null });

  const load = (): MockState => {
    if (!persist) return fresh();
    try {
      const raw = localStorage.getItem(storageKey);
      return raw ? { ...fresh(), ...(JSON.parse(raw) as MockState) } : fresh();
    } catch {
      return fresh();
    }
  };

  let state = load();
  const listeners = new Set<() => void>();

  // Keep tabs in sync: a verification link opened in another tab updates this one.
  if (persist && typeof window !== 'undefined') {
    window.addEventListener('storage', (e) => {
      if (e.key !== storageKey) return;
      state = load();
      listeners.forEach((l) => l());
    });
  }

  const save = () => {
    if (persist) {
      try {
        localStorage.setItem(storageKey, JSON.stringify(state));
      } catch {
        /* storage unavailable: keep state in memory */
      }
    }
    listeners.forEach((l) => l());
  };

  const wait = () => new Promise((r) => setTimeout(r, latency));
  const findByEmail = (email: string) => state.users.find((u) => u.institutionalEmail.toLowerCase() === email.trim().toLowerCase());
  const current = () => (state.session ? state.users.find((u) => u.id === state.session!.userId) ?? null : null);
  const toSession = (u: MockUser, mustChangePassword: boolean): Session => ({ user: publicUser(u), mustChangePassword, expiresAt: iso(8 * 3600_000) });

  const send = (mail: Omit<MockEmail, 'id' | 'sentAt'>) => {
    state.outbox = [{ ...mail, id: token().slice(0, 10), sentAt: new Date().toISOString() }, ...state.outbox].slice(0, 50);
  };

  const issue = (type: MockToken['type'], user: MockUser, email: string, ttl: number) => {
    state.tokens = state.tokens.filter((t) => !(t.userId === user.id && t.type === type)); // a new link invalidates the old one
    const t: MockToken = { token: token(), type, userId: user.id, email, expiresAt: iso(ttl) };
    state.tokens.push(t);
    return t;
  };

  const check = (value: string, type: MockToken['type']) => {
    const t = state.tokens.find((x) => x.token === value && x.type === type);
    if (!t) return { status: 'invalid' as const };
    if (new Date(t.expiresAt).getTime() < Date.now()) return { status: 'expired' as const, t };
    return { status: 'valid' as const, t };
  };

  return {
    async login(email, password) {
      await wait();
      const key = email.trim().toLowerCase();
      const a = state.attempts[key];
      if (a?.until && a.until > Date.now()) return { status: 'locked', retryAfterSeconds: Math.ceil((a.until - Date.now()) / 1000) };
      const u = findByEmail(email);
      const fail = () => {
        const count = (state.attempts[key]?.count ?? 0) + 1;
        state.attempts[key] = count >= maxAttempts ? { count: 0, until: Date.now() + 60_000 } : { count };
        save();
        return count >= maxAttempts ? ({ status: 'locked', retryAfterSeconds: 60 } as const) : ({ status: 'invalid' } as const);
      };
      if (!u) return fail();
      const usingOtp = !!u.oneTimePassword && password === u.oneTimePassword && new Date(u.oneTimePasswordExpiresAt ?? 0).getTime() > Date.now();
      const usingPassword = !!u.password && password === u.password;
      if (!usingOtp && !usingPassword) return fail();
      if (u.status === 'blocked') return { status: 'blocked', message: 'Your account is blocked. Please contact academic services.' };
      delete state.attempts[key];
      state.session = { userId: u.id, mustChangePassword: usingOtp };
      save();
      return { status: 'ok', session: toSession(u, usingOtp) };
    },

    async logout() {
      await wait();
      state.session = null;
      save();
    },

    async getSession() {
      await new Promise((r) => setTimeout(r, Math.min(latency, 250)));
      const u = current();
      if (!u || u.status === 'blocked') return null;
      return toSession(u, state.session!.mustChangePassword);
    },

    async changePassword({ current: cur, next }) {
      await wait();
      const u = current();
      if (!u) throw new Error('Not signed in.');
      const forced = state.session!.mustChangePassword;
      if (!forced && cur !== u.password) return { ok: false, error: 'wrong-current' };
      if (next === u.password || next === u.oneTimePassword) return { ok: false, error: 'same-as-current' };
      if (!isPasswordValid(next)) return { ok: false, error: 'policy' };
      u.password = next;
      delete u.oneTimePassword;
      delete u.oneTimePasswordExpiresAt;
      if (u.status === 'pending') u.status = 'active';
      state.session = { userId: u.id, mustChangePassword: false };
      send({ to: u.institutionalEmail, subject: 'Your StudyMatch password was changed', body: `Hi ${u.name.split(' ')[0]}, your password was just changed. If this wasn't you, contact academic services.` });
      save();
      return { ok: true };
    },

    async requestPasswordReset(email) {
      await wait();
      const u = findByEmail(email);
      if (u && u.status !== 'blocked') {
        const t = issue('reset', u, u.institutionalEmail, 30 * 60_000);
        send({
          to: u.institutionalEmail,
          subject: 'Reset your StudyMatch password',
          body: 'Use the link below to choose a new password. It expires in 30 minutes and works once.',
          link: { label: 'Choose a new password', href: `${base()}${routes.resetPassword(t.token)}` },
        });
        save();
      }
    },

    async checkResetToken(value) {
      await wait();
      return check(value, 'reset').status;
    },

    async resetPassword(value, next) {
      await wait();
      const r = check(value, 'reset');
      if (r.status !== 'valid') return { ok: false, error: r.status };
      if (!isPasswordValid(next)) return { ok: false, error: 'policy' };
      const u = state.users.find((x) => x.id === r.t.userId);
      if (!u) return { ok: false, error: 'invalid' };
      u.password = next;
      delete u.oneTimePassword;
      state.tokens = state.tokens.filter((t) => t !== r.t);
      send({ to: u.institutionalEmail, subject: 'Your StudyMatch password was reset', body: 'Your password was reset. You can now log in with the new password.' });
      save();
      return { ok: true };
    },

    async requestEmailVerification(personalEmail) {
      await wait();
      const u = current();
      if (!u) throw new Error('Not signed in.');
      u.personalEmail = personalEmail.trim();
      u.personalEmailVerified = false;
      const t = issue('verify', u, u.personalEmail, DAY);
      send({
        to: u.personalEmail,
        subject: 'Confirm your personal email',
        body: `Hi ${u.name.split(' ')[0]}, confirm this address to finish activating your StudyMatch account. The link expires in 24 hours.`,
        link: { label: 'Confirm my email', href: `${base()}${routes.verifyEmail(t.token)}` },
      });
      save();
    },

    async verifyEmail(value) {
      await wait();
      const r = check(value, 'verify');
      if (r.status !== 'valid') return { ok: false, error: r.status };
      const u = state.users.find((x) => x.id === r.t.userId);
      if (!u) return { ok: false, error: 'invalid' };
      u.personalEmail = r.t.email;
      u.personalEmailVerified = true;
      state.tokens = state.tokens.filter((t) => t !== r.t);
      save();
      return { ok: true, email: r.t.email };
    },

    async createAccount({ role, name, institutionalEmail }) {
      await wait();
      if (findByEmail(institutionalEmail)) throw new Error('An account with this institutional email already exists.');
      const code = otp();
      const u: MockUser = { id: `u-${token().slice(0, 8)}`, name: name.trim(), role, institutionalEmail: institutionalEmail.trim().toLowerCase(), personalEmailVerified: false, password: '', oneTimePassword: code, oneTimePasswordExpiresAt: iso(7 * DAY), status: 'pending' };
      state.users.push(u);
      send({ to: u.institutionalEmail, subject: 'Your StudyMatch account', body: `Hi ${u.name.split(' ')[0]}, an account was created for you. Log in with this one-time password; you'll choose your own password right after.`, code });
      save();
      return { id: u.id };
    },

    async registerStudent({ name, studentNumber, personalEmail, programme, curricularYear }) {
      await wait();
      if (state.users.some((u) => u.studentNumber === studentNumber)) throw new Error(`Student number ${studentNumber} is already registered.`);
      const institutionalEmail = `${studentNumber}@${studentDomain}`;
      const code = otp();
      const u: MockUser = {
        id: `u-${token().slice(0, 8)}`,
        name: name.trim(),
        role: 'student',
        studentNumber,
        institutionalEmail,
        personalEmail: personalEmail.trim(),
        personalEmailVerified: false,
        hasPersonalityTest: false,
        password: '',
        oneTimePassword: code,
        oneTimePasswordExpiresAt: iso(7 * DAY),
        status: 'pending',
      };
      state.users.push(u);
      send({
        to: u.personalEmail!,
        subject: 'Welcome to StudyMatch',
        body: `Hi ${u.name.split(' ')[0]}, you're registered in ${programme} (year ${curricularYear}). Log in with ${institutionalEmail} and this one-time password. It expires in 7 days.`,
        code,
      });
      save();
      return { id: u.id, institutionalEmail };
    },

    async setBlocked(userId, blocked, reason) {
      await wait();
      const u = state.users.find((x) => x.id === userId);
      if (!u) throw new Error('User not found.');
      u.status = blocked ? 'blocked' : u.password ? 'active' : 'pending';
      u.blockedReason = blocked ? reason : undefined;
      if (blocked && state.session?.userId === userId) state.session = null;
      save();
    },

    outbox: () => state.outbox,
    clearOutbox() {
      state.outbox = [];
      save();
    },
    users: () => state.users,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    reset() {
      state = fresh();
      save();
    },
  };
}
