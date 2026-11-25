import { Route, useLocation } from 'react-router-dom';
import { RoleLayout } from '../../components/layout/RoleLayout';
import { ChatsPage } from '../chats/ChatsPage';
import type { CurrentUser } from '../../types/currentUser';
import { TEACHER_SECTIONS } from './sections';
import { TeacherSubjectsPage } from './TeacherSubjectsPage';
import { TeacherSubjectLayout } from './TeacherSubjectLayout';
import { TeacherStudentsPage } from './TeacherStudentsPage';
import { TeacherStudentProfilePage } from './TeacherStudentProfilePage';
import { TeacherAssignmentsPage } from './TeacherAssignmentsPage';
import { TeacherGradesPage } from './TeacherGradesPage';
import { TeacherGroupsPage } from './TeacherGroupsPage';
import { SUBJECT_TABS } from './subjectTabs';

interface TeacherAreaProps {
  currentUser: CurrentUser;
  onLoggedOut: () => void;
}

export function TeacherArea({ currentUser, onLoggedOut }: TeacherAreaProps) {
  const { pathname } = useLocation();
  // inside a subject, the menu keeps the item of the tab that's open
  const tab = SUBJECT_TABS.find(item => new RegExp(`^/subject-editions/[^/]+/${item}`).test(pathname));
  return (
    <RoleLayout currentUser={currentUser} sections={TEACHER_SECTIONS} onLoggedOut={onLoggedOut} activePath={tab ? `/${tab}` : pathname}>
      <Route path="/chats" element={<ChatsPage me={currentUser.number} />} />
      <Route path="/chats/direct/:number" element={<ChatsPage me={currentUser.number} />} />
      <Route path="/chats/group/:chatId" element={<ChatsPage me={currentUser.number} />} />
      {SUBJECT_TABS.map(item => <Route key={item} path={`/${item}`} element={<TeacherSubjectsPage tab={item} />} />)}
      <Route path="/subject-editions/:editionId" element={<TeacherSubjectLayout />}>
        <Route path="students" element={<TeacherStudentsPage />} />
        <Route path="students/:number" element={<TeacherStudentProfilePage />} />
        <Route path="assignments" element={<TeacherAssignmentsPage />} />
        <Route path="assignments/:assignmentId/groups" element={<TeacherGroupsPage />} />
        <Route path="grades" element={<TeacherGradesPage />} />
      </Route>
    </RoleLayout>
  );
}
