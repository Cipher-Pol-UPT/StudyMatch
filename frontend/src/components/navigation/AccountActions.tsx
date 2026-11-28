import { Bell, KeyRound, LogOut } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Avatar, Menu, ThemeSwitcher, useToast } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import './accountActions.css';
import { NotificationsBell } from '../../pages/notifications/NotificationsBell';
import { useSignOut } from './useSignOut';

interface AccountActionsProps {
  user: CurrentUser;
  onLoggedOut: () => void;
}

// Account controls for the new role areas. The existing StudentArea keeps its own menu.
export function AccountActions({ user, onLoggedOut }: AccountActionsProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const toast = useToast();
  const { signOut, busy } = useSignOut(() => {
    navigate('/', { replace: true });
    onLoggedOut();
    toast.success(t('workspace.loggedOut'));
  });

  return (
    <div className="account-actions">
      <NotificationsBell />
      <ThemeSwitcher />
      <Menu
        label={t('nav.accountMenu')}
        trigger={
          <button type="button" className="role-account__trigger" aria-label={t('nav.accountMenu')}>
            <Avatar name={user.name} size={36} />
          </button>
        }
        items={[
          { type: 'label', label: user.name },
          { label: t('auth.changePassword'), icon: KeyRound, onSelect: () => navigate('/change-password') },
          { label: t('nav.notifications'), icon: Bell, onSelect: () => navigate('/notifications') },
          { type: 'separator' },
          { label: t('nav.logout'), icon: LogOut, danger: true, disabled: busy, onSelect: () => { void signOut(); } },
        ]}
      />
    </div>
  );
}
