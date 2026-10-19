import { ShieldCheck, ShieldOff, UserPlus } from 'lucide-react';
import type { NavigationSection } from '../../types/navigation';

export const ADMIN_SECTIONS: NavigationSection[] = [
  { key: 'createAccount', path: '/accounts/new', icon: UserPlus, issues: [23] },
  { key: 'blockAccount', path: '/accounts/block', icon: ShieldOff, issues: [28] },
  { key: 'unblockAccount', path: '/accounts/unblock', icon: ShieldCheck, issues: [34] },
];
