import type { PasswordRule } from '../../studymatch-ui';

export const PASSWORD_RULES: PasswordRule[] = [
  { id: 'length', labelKey: 'passwordRules.length', test: password => password.length >= 8 },
  { id: 'letter', labelKey: 'passwordRules.letter', test: password => /\p{L}/u.test(password) },
  { id: 'number', labelKey: 'passwordRules.number', test: password => /\d/.test(password) },
];
