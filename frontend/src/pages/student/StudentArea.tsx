import { House, LogOut } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useSignOut } from '../../components/navigation/useSignOut';
import { AppShell, Avatar, EmptyState, Logo, Menu, ThemeSwitcher, type MenuEntry, type NavItem, type NavSection } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import { AchievementsPage } from '../achievements/AchievementsPage';
import { ChatsPage } from '../chats/ChatsPage';
import { HomePage } from '../home/HomePage';
import { AccountPasswordPage } from '../password/AccountPasswordPage';
import { ProfilePage } from '../profile/ProfilePage';
import { StudyRoomsPage } from '../studyRooms/StudyRoomsPage';
import { ACCOUNT, SECTIONS, type Section } from './sections';
import '../../styles/appShell.css';
import './student.css';
import { useMyProfile } from './useMyProfile';

const PROFILE_PATH = '/profile';
const CHANGE_PASSWORD_PATH = '/change-password';

export interface StudentAreaProps {
  currentUser: CurrentUser;
  onLoggedOut: () => void;
}

export function StudentArea({ currentUser, onLoggedOut }: StudentAreaProps) {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const { profile, failed, showSavedProfile } = useMyProfile();
  const pages = [...SECTIONS, ...ACCOUNT];
  // a section's own pages count too, like an open chat under /chats
  const current = pages.find((p) => pathname === p.path || pathname.startsWith(`${p.path}/`));
  const toItem = (s: Section): NavItem => ({ label: t(`nav.${s.key}`), icon: s.icon, href: s.path, active: pathname.startsWith(s.path) });
  const comingSoon = SECTIONS.filter((s) => !s.ready);
  const nav: NavSection[] = [
    { items: [{ label: t('nav.home'), icon: House, href: '/', active: pathname === '/' }, ...SECTIONS.filter((s) => s.ready).map(toItem)] },
    ...(comingSoon.length ? [{ title: t('nav.comingSoon'), items: comingSoon.map(toItem), muted: true }] : []),
  ];

  return (
    <AppShell
      brand={<Logo size={28} />}
      nav={nav}
      title={t(current ? `nav.${current.key}` : pathname === '/achievements' ? 'achievements.title' : 'nav.home')}
      actions={<><ThemeSwitcher /><ProfileMenu user={currentUser} avatarUrl={profile?.avatarUrl ?? undefined} onLoggedOut={onLoggedOut} /></>}
      linkAs={Link}
      className="app-shell"
      routeKey={pathname}
    >
      <Routes>
        <Route path="/" element={<HomePage currentUser={currentUser} profile={profile} failed={failed} />} />
        <Route path={PROFILE_PATH} element={<ProfilePage onSaved={showSavedProfile} />} />
        <Route path="/study-rooms" element={<StudyRoomsPage currentUser={currentUser} />} />
        <Route path="/chats" element={<ChatsPage />} />
        <Route path="/chats/direct/:number" element={<ChatsPage />} />
        <Route path={CHANGE_PASSWORD_PATH} element={<AccountPasswordPage />} />
        <Route path="/achievements" element={<AchievementsPage profile={profile} failed={failed} />} />
        {pages.filter((p) => ![PROFILE_PATH, '/study-rooms', '/chats', CHANGE_PASSWORD_PATH].includes(p.path)).map((p) => (
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
  const { signOut, busy } = useSignOut(onLoggedOut);

  const items: MenuEntry[] = [
    { type: 'label', label: user.name },
    ...ACCOUNT.map((a): MenuEntry => ({ label: t(`nav.${a.key}`), icon: a.icon, onSelect: () => navigate(a.path) })),
    { type: 'separator' },
    { label: t('nav.logout'), icon: LogOut, danger: true, disabled: busy, onSelect: () => { void signOut(); } },
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