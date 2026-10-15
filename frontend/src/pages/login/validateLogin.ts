import type { FieldError } from '../../api/errors';
import type { LoginInput } from '../../types/forms';

const EMAIL_FORMAT = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateLogin({ email, password }: LoginInput): FieldError[] {
  const errors: FieldError[] = [];

  if (email === '') {
    errors.push({ field: 'email', code: 'NOT_BLANK' });
  } else if (!EMAIL_FORMAT.test(email)) {
    errors.push({ field: 'email', code: 'EMAIL' });
  }

  if (password === '') {
    errors.push({ field: 'password', code: 'NOT_BLANK' });
  }

  return errors;
}
