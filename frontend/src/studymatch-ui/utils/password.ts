/**
 * Password policy used by StudyMatch (Change Password use case):
 * at least 8 characters, with letters and numbers, and different from the current one.
 */
export interface PasswordRuleContext {
  /** The user's current password, when known. Enables the "different" rule. */
  current?: string;
}

export interface PasswordRule {
  id: string;
  label: string;
  test: (password: string, ctx: PasswordRuleContext) => boolean;
  /** Only evaluate (and show) the rule when this returns true. */
  when?: (ctx: PasswordRuleContext) => boolean;
}

export interface PasswordRuleResult {
  id: string;
  label: string;
  ok: boolean;
}

export const STUDYMATCH_PASSWORD_RULES: PasswordRule[] = [
  { id: 'length', label: 'At least 8 characters', test: (pw) => pw.length >= 8 },
  { id: 'letter', label: 'At least one letter', test: (pw) => /\p{L}/u.test(pw) },
  { id: 'number', label: 'At least one number', test: (pw) => /\d/.test(pw) },
  {
    id: 'different',
    label: 'Different from your current password',
    test: (pw, { current }) => !current || pw !== current,
    when: ({ current }) => !!current,
  },
];

export function checkPassword(password: string, { rules = STUDYMATCH_PASSWORD_RULES, current }: { rules?: PasswordRule[]; current?: string } = {}): PasswordRuleResult[] {
  const ctx = { current };
  return rules.filter((r) => !r.when || r.when(ctx)).map((r) => ({ id: r.id, label: r.label, ok: r.test(password, ctx) }));
}

export function isPasswordValid(password: string, options?: { rules?: PasswordRule[]; current?: string }) {
  return checkPassword(password, options).every((r) => r.ok);
}

export type PasswordStrength = 0 | 1 | 2 | 3 | 4;
export const STRENGTH_LABELS = ['Too weak', 'Weak', 'Okay', 'Strong', 'Very strong'] as const;

/** A quick, dependency-free strength estimate (0–4). Not a replacement for server-side checks. */
export function passwordStrength(password: string): PasswordStrength {
  if (!password) return 0;
  let score = 0;
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++;
  if (/\d/.test(password) && /\p{L}/u.test(password)) score++;
  if (/[^\p{L}\d]/u.test(password)) score++;
  if (/^(.)\1+$/.test(password) || /^(?:1234|password|qwerty|abcd)/i.test(password)) score = Math.min(score, 1);
  return Math.min(4, score) as PasswordStrength;
}
