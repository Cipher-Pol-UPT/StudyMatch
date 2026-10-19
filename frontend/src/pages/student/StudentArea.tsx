import { House, LogOut } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { logout } from '../../api/auth';
import { AppShell, Avatar, EmptyState, Logo, Menu, type MenuEntry, type NavItem } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import { HomePage } from '../home/HomePage';
import { ACCOUNT, SECTIONS } from './sections';
import './student.css';
import { useMyProfile } from './useMyProfile';

export interface StudentAreaProps {
  currentUser: CurrentUser;
  onLoggedOut: () => void;
}

export function StudentArea({ currentUser, onLoggedOut }: StudentAreaProps) {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const { profile, failed } = useMyProfile();
  const pages = [...SECTIONS, ...ACCOUNT];
  const current = pages.find((p) => p.path === pathname);
  const nav: NavItem[] = [
    { label: t('nav.home'), icon: House, href: '/', active: pathname === '/' },
    ...SECTIONS.map((s) => ({ label: t(`nav.${s.key}`), icon: s.icon, href: s.path, active: pathname.startsWith(s.path) })),
  ];

  return (
    <AppShell
      brand={<Logo size={28} />}
      nav={[{ items: nav }]}
      title={t(current ? `nav.${current.key}` : 'nav.home')}
      actions={<ProfileMenu user={currentUser} avatarUrl={profile?.avatarUrl ?? undefined} onLoggedOut={onLoggedOut} />}
      linkAs={Link}
      className="student-shell"
      routeKey={pathname}
    >
      <Routes>
        <Route path="/" element={<HomePage currentUser={currentUser} profile={profile} failed={failed} />} />
        {pages.map((p) => (
          <Route key={p.path} path={p.path} element={<EmptyState icon={p.icon} title={t(`nav.${p.key}`)} body={t('placeholder.comingSoon')} />} />
        ))}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
  );
}

interface ProfileMenuProps {
  user: CurrentUser;
  avatarUrl?: string;
  onLoggedOut: () => void;
}

function ProfileMenu({ user, avatarUrl, onLoggedOut }: ProfileMenuProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const signOut = () => {
    logout().catch(() => undefined).finally(onLoggedOut);
  };

  const items: MenuEntry[] = [
    { type: 'label', label: user.name },
    ...ACCOUNT.map((a): MenuEntry => ({ label: t(`nav.${a.key}`), icon: a.icon, onSelect: () => navigate(a.path) })),
    { type: 'separator' },
    { label: t('nav.logout'), icon: LogOut, danger: true, onSelect: signOut },
  ];

  return (
    <Menu
      label={t('nav.accountMenu')}
      items={items}
      trigger={
        <button type="button" className="profile-menu__trigger" aria-label={t('nav.accountMenu')}>
          <Avatar name={user.name} src={avatarUrl} size={36} />
        </button>
      }
    />
  );
}