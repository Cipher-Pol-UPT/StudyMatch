import { House } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { AppShell, EmptyState, Logo, ThemeSwitcher, type MenuEntry, type NavItem } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import { HomePage } from '../home/HomePage';
import { ProfilePage } from '../profile/ProfilePage';
import { AccountMenu } from '../shell/AccountMenu';
import { StudyRoomsPage } from '../studyRooms/StudyRoomsPage';
import { ACCOUNT, SECTIONS } from './sections';
import './student.css';
import { useMyProfile } from './useMyProfile';

const PROFILE_PATH = '/profile';

export interface StudentAreaProps {
  currentUser: CurrentUser;
  onLoggedOut: () => void;
}

export function StudentArea({ currentUser, onLoggedOut }: StudentAreaProps) {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { profile, failed, showSavedProfile } = useMyProfile();
  const pages = [...SECTIONS, ...ACCOUNT];
  const current = pages.find((p) => p.path === pathname);
  const nav: NavItem[] = [
    { label: t('nav.home'), icon: House, href: '/', active: pathname === '/' },
    ...SECTIONS.map((s) => ({ label: t(`nav.${s.key}`), icon: s.icon, href: s.path, active: pathname.startsWith(s.path) })),
  ];
  const accountItems: MenuEntry[] = ACCOUNT.map((a) => ({ label: t(`nav.${a.key}`), icon: a.icon, onSelect: () => navigate(a.path) }));

  return (
    <AppShell
      brand={<Logo size={28} />}
      nav={[{ items: nav }]}
      title={t(current ? `nav.${current.key}` : 'nav.home')}
      actions={<><ThemeSwitcher /><AccountMenu user={currentUser} avatarUrl={profile?.avatarUrl ?? undefined} items={accountItems} onLoggedOut={onLoggedOut} /></>}
      linkAs={Link}
      className="student-shell"
      routeKey={pathname}
    >
      <Routes>
        <Route path="/" element={<HomePage currentUser={currentUser} profile={profile} failed={failed} />} />
        <Route path={PROFILE_PATH} element={<ProfilePage onSaved={showSavedProfile} />} />
        <Route path="/study-rooms" element={<StudyRoomsPage currentUser={currentUser} />} />
        {pages.filter((p) => p.path !== PROFILE_PATH && p.path !== '/study-rooms').map((p) => (
          <Route key={p.path} path={p.path} element={<EmptyState icon={p.icon} title={t(`nav.${p.key}`)} body={t('placeholder.comingSoon')} />} />
        ))}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
  );
}
