import { Route } from 'react-router-dom';
import { RoleLayout } from '../../components/layout/RoleLayout';
import type { CurrentUser } from '../../types/currentUser';
import { CreateAccountPage } from '../accounts/CreateAccountPage';
import { ADMIN_SECTIONS } from './sections';

interface AdminAreaProps {
  currentUser: CurrentUser;
  onLoggedOut: () => void;
}

export function AdminArea({ currentUser, onLoggedOut }: AdminAreaProps) {
  const domain = currentUser.institutionalEmail.slice(currentUser.institutionalEmail.indexOf('@') + 1);
  return (
    <RoleLayout currentUser={currentUser} sections={ADMIN_SECTIONS} onLoggedOut={onLoggedOut}>
      <Route path="/accounts/new" element={<CreateAccountPage domain={domain} />} />
    </RoleLayout>
  );
}
