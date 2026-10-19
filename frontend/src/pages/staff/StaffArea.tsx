import { Route } from 'react-router-dom';
import { RoleLayout } from '../../components/layout/RoleLayout';
import type { CurrentUser } from '../../types/currentUser';
import { STAFF_SECTIONS } from './sections';
import { StaffStudentsPage } from './StaffStudentsPage';
import { StaffStudentDetailsPage } from './StaffStudentDetailsPage';

interface StaffAreaProps {
  currentUser: CurrentUser;
  onLoggedOut: () => void;
}

export function StaffArea({ currentUser, onLoggedOut }: StaffAreaProps) {
  return (
    <RoleLayout currentUser={currentUser} sections={STAFF_SECTIONS} onLoggedOut={onLoggedOut}>
      <Route path="/students" element={<StaffStudentsPage />} />
      <Route path="/students/:number" element={<StaffStudentDetailsPage />} />
    </RoleLayout>
  );
}
