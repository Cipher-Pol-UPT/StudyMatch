import { ShieldCheck, ShieldOff, UserPlus } from 'lucide-react';
import type { NavigationSection } from '../../types/navigation';

export const ADMIN_SECTIONS: NavigationSection[] = [
  { key: 'createAccount', path: '/accounts/new', icon: UserPlus },
  { key: 'blockAccount', path: '/accounts/block', icon: ShieldOff },
  { key: 'unblockAccount', path: '/accounts/unblock', icon: ShieldCheck },
];
