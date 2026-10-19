import { ShieldPlus, UserPlus } from 'lucide-react';
import type { IconComponent } from '../../studymatch-ui';
import type { Role } from '../../types/enums';

export interface WorkspaceSection {
  key: string;
  path: string;
  icon: IconComponent;
  color: string;
  bg: string;
}

export const REGISTER_STUDENT_PATH = '/students/register';
export const CREATE_ACCOUNT_PATH = '/accounts/new';

const SECTIONS_BY_ROLE: Partial<Record<Role, WorkspaceSection[]>> = {
  STAFF: [
    { key: 'registerStudent', path: REGISTER_STUDENT_PATH, icon: UserPlus, color: 'var(--sm-lead)', bg: 'var(--sm-tint-lavender)' },
  ],
  ADMIN: [
    { key: 'createAccount', path: CREATE_ACCOUNT_PATH, icon: ShieldPlus, color: 'var(--sm-exec)', bg: 'var(--sm-tint-mint)' },
  ],
};

export function sectionsFor(role: Role): WorkspaceSection[] {
  return SECTIONS_BY_ROLE[role] ?? [];
}
