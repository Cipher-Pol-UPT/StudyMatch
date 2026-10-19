import { House } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell, Logo, ThemeSwitcher, type NavItem } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import { CreateAccountPage } from '../accounts/CreateAccountPage';
import { RegisterStudentPage } from '../registration/RegisterStudentPage';
import { AccountMenu } from '../shell/AccountMenu';
import '../student/student.css';
import { WorkspaceHome } from './WorkspaceHome';
import { CREATE_ACCOUNT_PATH, REGISTER_STUDENT_PATH, sectionsFor } from './workspaceSections';

export interface WorkspaceAreaProps {
  currentUser: CurrentUser;
  onLoggedOut: () => void;
}

export function WorkspaceArea({ currentUser, onLoggedOut }: WorkspaceAreaProps) {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const sections = sectionsFor(currentUser.role);
  const current = sections.find(section => section.path === pathname);
  const nav: NavItem[] = [
    { label: t('nav.home'), icon: House, href: '/', active: pathname === '/' },
    ...sections.map(section => ({ label: t(`nav.${section.key}`), icon: section.icon, href: section.path, active: pathname.startsWith(section.path) })),
  ];

  return (
    <AppShell
      brand={<Logo size={28} />}
      nav={[{ items: nav }]}
      title={t(current ? `nav.${current.key}` : 'nav.home')}
      actions={<><ThemeSwitcher /><AccountMenu user={currentUser} onLoggedOut={onLoggedOut} /></>}
      linkAs={Link}
      className="student-shell"
      routeKey={pathname}
    >
      <Routes>
        <Route path="/" element={<WorkspaceHome currentUser={currentUser} sections={sections} />} />
        {currentUser.role === 'STAFF' && <Route path={REGISTER_STUDENT_PATH} element={<RegisterStudentPage />} />}
        {currentUser.role === 'ADMIN' && (
          <Route path={CREATE_ACCOUNT_PATH} element={<CreateAccountPage domain={institutionDomainOf(currentUser)} />} />
        )}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
  );
}

function institutionDomainOf(user: CurrentUser) {
  return user.institutionalEmail.slice(user.institutionalEmail.indexOf('@') + 1);
}
