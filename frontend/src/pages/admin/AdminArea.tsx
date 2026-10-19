import { RoleLayout } from '../../components/layout/RoleLayout';
import type { CurrentUser } from '../../types/currentUser';
import { ADMIN_SECTIONS } from './sections';

interface AdminAreaProps {
  currentUser: CurrentUser;
  onLoggedOut: () => void;
}

export function AdminArea({ currentUser, onLoggedOut }: AdminAreaProps) {
  return <RoleLayout currentUser={currentUser} sections={ADMIN_SECTIONS} onLoggedOut={onLoggedOut} />;
}
