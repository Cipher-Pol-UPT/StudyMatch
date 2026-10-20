import { Bell, House } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell, EmptyState, Logo, type NavItem } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import type { NavigationSection } from '../../types/navigation';
import { AccountPasswordPage } from '../../pages/password/AccountPasswordPage';
import { FeaturePlaceholder } from '../feedback/FeaturePlaceholder';
import { AccountActions } from '../navigation/AccountActions';
import { RoleHomePage } from './RoleHomePage';
import '../../styles/roleLayout.css';

interface RoleLayoutProps {
  currentUser: CurrentUser;
  sections: NavigationSection[];
  onLoggedOut: () => void;
  activePath?: string;
  // the <Route>s of the role's ready sections
  children?: ReactNode;
}

export function RoleLayout({ currentUser, sections, onLoggedOut, activePath, children }: RoleLayoutProps) {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const selectedPath = activePath ?? pathname;
  const current = sections.find(section => section.path === selectedPath)
    ?? sections.find(section => selectedPath.startsWith(`${section.path}/`));
  const nav: NavItem[] = [
    { label: t('nav.home'), icon: House, href: '/', active: pathname === '/' },
    ...sections.map(section => ({
      label: t(`workspace.sections.${section.key}`),
      icon: section.icon,
      href: section.path,
      active: section === current,
    })),
  ];
  const title = current ? t(`workspace.sections.${current.key}`)
    : pathname === '/change-password' ? t('auth.changePassword')
    : pathname === '/notifications' ? t('nav.notifications') : t('nav.home');

  return (
    <AppShell
      brand={<Logo size={28} />}
      nav={[{ title: t(`workspace.roles.${currentUser.role}`), items: nav }]}
      title={title}
      actions={<AccountActions user={currentUser} onLoggedOut={onLoggedOut} />}
      linkAs={Link}
      className="role-shell"
      routeKey={pathname}
    >
      <Routes>
        <Route path="/" element={<RoleHomePage currentUser={currentUser} sections={sections} />} />
        {children}
        {sections.filter(section => !section.ready).map(section => (
          <Route key={section.path} path={section.path} element={<FeaturePlaceholder section={section} />} />
        ))}
        <Route path="/change-password" element={<AccountPasswordPage />} />
        <Route path="/notifications" element={<EmptyState icon={Bell} title={t('nav.notifications')} body={t('placeholder.comingSoon')} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
  );
}
