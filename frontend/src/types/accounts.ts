import type { Role } from './enums';

export type EmployeeRole = Extract<Role, 'STAFF' | 'TEACHER'>;

export interface CreatedAccount {
  number: number;
  name: string;
  institutionalEmail: string;
  type: EmployeeRole;
}
