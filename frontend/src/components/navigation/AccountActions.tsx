import { Bell, KeyRound, LogOut } from 'lucide-react';
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { logout } from '../../api/auth';
import { isBackendError } from '../../api/errors';
import { Avatar, Button, Menu, ThemeSwitcher, useToast } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import './accountActions.css';

interface AccountActionsProps {
  user: CurrentUser;
  onLoggedOut: () => void;
}

// Account controls for the new role areas. The existing StudentArea keeps its own menu.
export function AccountActions({ user, onLoggedOut }: AccountActionsProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);

  async function signOut() {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    try {
      await logout();
    } catch (error) {
      if (!isBackendError(error) || error.code !== 'UNAUTHENTICATED') {
        toast.danger(t('workspace.logoutFailed'));
        pending.current = false;
        setBusy(false);
        return;
      }
    }
    navigate('/', { replace: true });
    onLoggedOut();
    toast.success(t('workspace.loggedOut'));
  }

  return (
    <div className="account-actions">
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
      <Button size="sm" variant="ghost" icon={LogOut} loading={busy} onClick={() => { void signOut(); }}>
        {t('nav.logout')}
      </Button>
    </div>
  );
}
