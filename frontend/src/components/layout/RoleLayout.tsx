import { House } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell, Logo, type NavItem, type NavSection } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import type { NavigationSection } from '../../types/navigation';
import { AccountPasswordPage } from '../../pages/password/AccountPasswordPage';
import { FeaturePlaceholder } from '../feedback/FeaturePlaceholder';
import { AccountActions } from '../navigation/AccountActions';
import { NotificationsPage } from '../../pages/notifications/NotificationsPage';
import { NotificationsProvider } from '../../pages/notifications/NotificationsProvider';
import { RoleHomePage } from './RoleHomePage';
import '../../styles/appShell.css';
import '../../styles/roleLayout.css';
import { useUnreadChats } from '../../hooks/useUnreadChats';

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
  // the unread messages show on the Chats item, for the roles that have one (#201)
  const unread = useUnreadChats(sections.some(section => section.key === 'chats'));
  const toItem = (section: NavigationSection): NavItem => ({
    label: t(`workspace.sections.${section.key}`),
    icon: section.icon,
    href: section.path,
    active: section === current,
    badge: section.key === 'chats' ? unread : undefined,
  });
  const comingSoon = sections.filter(section => !section.ready);
  const nav: NavSection[] = [
    {
      title: t(`workspace.roles.${currentUser.role}`),
      items: [
        { label: t('nav.home'), icon: House, href: '/', active: pathname === '/' },
        ...sections.filter(section => section.ready).map(toItem),
      ],
    },
    ...(comingSoon.length ? [{ title: t('nav.comingSoon'), items: comingSoon.map(toItem), muted: true }] : []),
  ];
  const title = current ? t(`workspace.sections.${current.key}`)
    : pathname === '/change-password' ? t('auth.changePassword')
    : pathname === '/notifications' ? t('nav.notifications') : t('nav.home');

  return (
    <NotificationsProvider role={currentUser.role}>
    <AppShell
      brand={<Logo size={28} />}
      nav={nav}
      title={title}
      actions={<AccountActions user={currentUser} onLoggedOut={onLoggedOut} />}
      linkAs={Link}
      className="app-shell"
      routeKey={pathname}
    >
      <Routes>
        <Route path="/" element={<RoleHomePage currentUser={currentUser} sections={sections} />} />
        {children}
        {sections.filter(section => !section.ready).map(section => (
          <Route key={section.path} path={section.path} element={<FeaturePlaceholder section={section} />} />
        ))}
        <Route path="/change-password" element={<AccountPasswordPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
    </NotificationsProvider>
  );
}
