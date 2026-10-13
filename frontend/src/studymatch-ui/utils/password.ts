export interface PasswordRule { id: string; labelKey: string; test: (password: string) => boolean }
export interface PasswordRuleResult { id: string; labelKey: string; ok: boolean }
export function checkPassword(password: string, rules: PasswordRule[]): PasswordRuleResult[] {
  return rules.map(rule => ({ id: rule.id, labelKey: rule.labelKey, ok: rule.test(password) }));
}
