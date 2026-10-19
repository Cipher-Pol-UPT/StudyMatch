import { Bell, KeyRound } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Navigate, Route, Routes } from 'react-router-dom';
import { FeaturePlaceholder } from '../../components/feedback/FeaturePlaceholder';
import { RoleLayout } from '../../components/layout/RoleLayout';
import { EmptyState } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import { AdminHomePage } from './AdminHomePage';
import { ADMIN_SECTIONS } from './sections';

interface AdminAreaProps {
  currentUser: CurrentUser;
  onLoggedOut: () => void;
}

export function AdminArea({ currentUser, onLoggedOut }: AdminAreaProps) {
  const { t } = useTranslation();

  return (
    <RoleLayout currentUser={currentUser} sections={ADMIN_SECTIONS} onLoggedOut={onLoggedOut}>
      <Routes>
        <Route path="/" element={<AdminHomePage currentUser={currentUser} />} />
        {ADMIN_SECTIONS.map(section => (
          <Route key={section.path} path={section.path} element={<FeaturePlaceholder section={section} />} />
        ))}
        <Route path="/change-password" element={<EmptyState icon={KeyRound} title={t('auth.changePassword')} body={t('placeholder.comingSoon')} />} />
        <Route path="/notifications" element={<EmptyState icon={Bell} title={t('nav.notifications')} body={t('placeholder.comingSoon')} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </RoleLayout>
  );
}
