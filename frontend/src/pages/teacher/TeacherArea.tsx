import { Route, useLocation } from 'react-router-dom';
import { RoleLayout } from '../../components/layout/RoleLayout';
import type { CurrentUser } from '../../types/currentUser';
import { TEACHER_SECTIONS } from './sections';
import { TeacherSubjectsPage } from './TeacherSubjectsPage';
import { TeacherSubjectLayout } from './TeacherSubjectLayout';
import { TeacherStudentsPage } from './TeacherStudentsPage';
import { TeacherStudentProfilePage } from './TeacherStudentProfilePage';

interface TeacherAreaProps {
  currentUser: CurrentUser;
  onLoggedOut: () => void;
}

export function TeacherArea({ currentUser, onLoggedOut }: TeacherAreaProps) {
  const { pathname } = useLocation();
  return (
    <RoleLayout currentUser={currentUser} sections={TEACHER_SECTIONS} onLoggedOut={onLoggedOut} activePath={pathname.startsWith('/subject-editions/') ? '/students' : pathname}>
      <Route path="/students" element={<TeacherSubjectsPage />} />
      <Route path="/subject-editions/:editionId" element={<TeacherSubjectLayout />}>
        <Route path="students" element={<TeacherStudentsPage />} />
        <Route path="students/:number" element={<TeacherStudentProfilePage />} />
      </Route>
    </RoleLayout>
  );
}
