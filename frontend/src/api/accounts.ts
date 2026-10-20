import type { CreatedAccount } from '../types/accounts';
import type { CreateAccountInput } from '../types/forms';
import { request } from './client';

export function createAccount(account: CreateAccountInput): Promise<CreatedAccount> {
  return request<CreatedAccount>('POST', '/users/', account);
}
