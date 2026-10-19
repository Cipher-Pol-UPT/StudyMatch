import { Bell, KeyRound } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Navigate, Route, Routes } from 'react-router-dom';
import { FeaturePlaceholder } from '../../components/feedback/FeaturePlaceholder';
import { RoleLayout } from '../../components/layout/RoleLayout';
import { EmptyState } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import { StaffHomePage } from './StaffHomePage';
import { STAFF_SECTIONS } from './sections';
import { StaffStudentsPage } from './StaffStudentsPage';
import { StaffStudentDetailsPage } from './StaffStudentDetailsPage';

interface StaffAreaProps {
  currentUser: CurrentUser;
  onLoggedOut: () => void;
}

export function StaffArea({ currentUser, onLoggedOut }: StaffAreaProps) {
  const { t } = useTranslation();

  return (
    <RoleLayout currentUser={currentUser} sections={STAFF_SECTIONS} onLoggedOut={onLoggedOut}>
      <Routes>
        <Route path="/" element={<StaffHomePage currentUser={currentUser} />} />
        <Route path="/students" element={<StaffStudentsPage />} />
        <Route path="/students/:number" element={<StaffStudentDetailsPage />} />
        {STAFF_SECTIONS.filter(section => section.key !== 'students').map(section => (
          <Route key={section.path} path={section.path} element={<FeaturePlaceholder section={section} />} />
        ))}
        <Route path="/change-password" element={<EmptyState icon={KeyRound} title={t('auth.changePassword')} body={t('placeholder.comingSoon')} />} />
        <Route path="/notifications" element={<EmptyState icon={Bell} title={t('nav.notifications')} body={t('placeholder.comingSoon')} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </RoleLayout>
  );
}
