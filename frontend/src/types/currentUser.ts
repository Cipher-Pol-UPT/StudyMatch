import type { Role } from './enums';

export interface CurrentUser {
  number: number;
  name: string;
  institutionalEmail: string;
  role: Role;
  mustChangePassword: boolean;
  personalityTestRequired: boolean;
}
