import type { Role } from './enums';

export interface Session {
  name: string;
  role: Role;
  mustChangePassword: boolean;
  personalityTestPending: boolean;
}
