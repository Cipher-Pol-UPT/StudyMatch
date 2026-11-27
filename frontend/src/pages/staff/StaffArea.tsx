import { Route } from 'react-router-dom';
import { RoleLayout } from '../../components/layout/RoleLayout';
import type { CurrentUser } from '../../types/currentUser';
import { RegisterStudentPage } from '../registration/RegisterStudentPage';
import { STAFF_SECTIONS } from './sections';
import { StaffAnalyticsPage } from './StaffAnalyticsPage';
import { StaffAttendancePage } from './StaffAttendancePage';
import { StaffCategoriesPage } from './StaffCategoriesPage';
import { StaffEnrolmentsPage } from './StaffEnrolmentsPage';
import { StaffStudentsPage } from './StaffStudentsPage';
import { StaffStudentDetailsPage } from './StaffStudentDetailsPage';
import { StaffSubjectsPage } from './StaffSubjectsPage';

interface StaffAreaProps {
  currentUser: CurrentUser;
  onLoggedOut: () => void;
}

export function StaffArea({ currentUser, onLoggedOut }: StaffAreaProps) {
  return (
    <RoleLayout currentUser={currentUser} sections={STAFF_SECTIONS} onLoggedOut={onLoggedOut}>
      <Route path="/students" element={<StaffStudentsPage />} />
      <Route path="/students/register" element={<RegisterStudentPage />} />
      <Route path="/students/:number" element={<StaffStudentDetailsPage />} />
      <Route path="/enrolments" element={<StaffEnrolmentsPage />} />
      <Route path="/attendance" element={<StaffAttendancePage />} />
      <Route path="/categories" element={<StaffCategoriesPage />} />
      <Route path="/subjects" element={<StaffSubjectsPage />} />
      <Route path="/analytics" element={<StaffAnalyticsPage />} />
    </RoleLayout>
  );
}
