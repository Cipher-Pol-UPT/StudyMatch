import { Bell, KeyRound } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { FeaturePlaceholder } from '../../components/feedback/FeaturePlaceholder';
import { RoleLayout } from '../../components/layout/RoleLayout';
import { EmptyState } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import { TeacherHomePage } from './TeacherHomePage';
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
  const { t } = useTranslation();
  const { pathname } = useLocation();
  return (
    <RoleLayout currentUser={currentUser} sections={TEACHER_SECTIONS} onLoggedOut={onLoggedOut} activePath={pathname.startsWith('/subject-editions/') ? '/students' : pathname}>
      <Routes>
        <Route path="/" element={<TeacherHomePage currentUser={currentUser} />} />
        <Route path="/students" element={<TeacherSubjectsPage titleKey="workspace.sections.students" />} />
        <Route path="/subjects" element={<TeacherSubjectsPage />} />
        <Route path="/subject-editions/:editionId" element={<TeacherSubjectLayout />}>
          <Route path="students" element={<TeacherStudentsPage />} />
          <Route path="students/:number" element={<TeacherStudentProfilePage />} />
        </Route>
        {TEACHER_SECTIONS.filter(section => section.key !== 'mySubjects' && section.key !== 'students').map(section => (
          <Route key={section.path} path={section.path} element={<FeaturePlaceholder section={section} />} />
        ))}
        <Route path="/change-password" element={<EmptyState icon={KeyRound} title={t('auth.changePassword')} body={t('placeholder.comingSoon')} />} />
        <Route path="/notifications" element={<EmptyState icon={Bell} title={t('nav.notifications')} body={t('placeholder.comingSoon')} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </RoleLayout>
  );
}
