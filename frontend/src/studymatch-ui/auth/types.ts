/**
 * Authentication contract used by every auth block.
 * Implement `AuthAdapter` for your backend, or start with `createMockAuthAdapter()`
 * (in-browser, seeded users) or `createHttpAuthAdapter()` (REST endpoints).
 */

export type Role = 'student' | 'teacher' | 'staff' | 'admin';
export type AccountStatus = 'active' | 'pending' | 'blocked';

export interface AuthUser {
  id: string;
  name: string;
  role: Role;
  institutionalEmail: string;
  personalEmail?: string;
  personalEmailVerified?: boolean;
  /** Students only. */
  studentNumber?: string;
  /** A student with an approved Tutor Role in at least one subject. */
  isTutor?: boolean;
  /** Students only: a personality test exists for the current academic year (GR1). */
  hasPersonalityTest?: boolean;
  avatarUrl?: string;
}

export interface Session {
  user: AuthUser;
  /** Logged in with a one-time password: the user must set a new password first. */
  mustChangePassword?: boolean;
  /** Opaque token, if your backend uses bearer tokens. Prefer httpOnly cookies. */
  token?: string;
  expiresAt?: string;
}

export type LoginResult =
  | { status: 'ok'; session: Session }
  | { status: 'invalid' }
  | { status: 'blocked'; message?: string }
  /** Too many attempts; try again later. */
  | { status: 'locked'; retryAfterSeconds?: number };

export type ChangePasswordResult = { ok: true } | { ok: false; error: 'wrong-current' | 'same-as-current' | 'policy'; message?: string };
export type TokenCheck = 'valid' | 'expired' | 'invalid';
export type ResetPasswordResult = { ok: true } | { ok: false; error: 'expired' | 'invalid' | 'policy'; message?: string };
export type VerifyEmailResult = { ok: true; email: string } | { ok: false; error: 'expired' | 'invalid' };

export interface CreateAccountInput {
  /** Admins create Staff and Teacher accounts only. Students are registered by Staff. */
  role: 'staff' | 'teacher';
  name: string;
  institutionalEmail: string;
}

export interface RegisterStudentInput {
  name: string;
  studentNumber: string;
  personalEmail: string;
  programme: string;
  curricularYear: number;
}

export interface AuthAdapter {
  login(email: string, password: string): Promise<LoginResult>;
  logout(): Promise<void>;
  /** Restore the session on page load. */
  getSession(): Promise<Session | null>;
  /** `current` is omitted on the forced first-login change (the one-time password was just verified). */
  changePassword(input: { current?: string; next: string }): Promise<ChangePasswordResult>;
  /** Always resolves, whether or not the email exists (no account enumeration). */
  requestPasswordReset(email: string): Promise<void>;
  checkResetToken?(token: string): Promise<TokenCheck>;
  resetPassword(token: string, next: string): Promise<ResetPasswordResult>;
  /** Sends a verification link to the personal email. */
  requestEmailVerification(personalEmail: string): Promise<void>;
  verifyEmail(token: string): Promise<VerifyEmailResult>;
  /** Admin. */
  createAccount?(input: CreateAccountInput): Promise<{ id: string }>;
  /** Staff. */
  registerStudent?(input: RegisterStudentInput): Promise<{ id: string; institutionalEmail: string }>;
  /** Admin. Accounts are blocked, never deleted. */
  setBlocked?(userId: string, blocked: boolean, reason?: string): Promise<void>;
}

/** Where the user must go after logging in, in order. */
export type AuthStep = 'login' | 'change-password' | 'verify-email' | 'personality-test' | 'home';

export function nextStep(session: Session | null, { requirePersonalEmail = true }: { requirePersonalEmail?: boolean } = {}): AuthStep {
  if (!session) return 'login';
  if (session.mustChangePassword) return 'change-password';
  if (requirePersonalEmail && !session.user.personalEmailVerified) return 'verify-email';
  if (session.user.role === 'student' && !session.user.hasPersonalityTest) return 'personality-test';
  return 'home';
}

export const ROLE_LABEL: Record<Role, string> = { student: 'Student', teacher: 'Teacher', staff: 'Staff', admin: 'Admin' };
