import { LogOut } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { logout } from '../../api/auth';
import { Button, useToast } from '../../studymatch-ui';
import './LogoutButton.css';

export interface LogoutButtonProps {
  onLoggedOut: () => void;
}

export function LogoutButton({ onLoggedOut }: LogoutButtonProps) {
  const { t } = useTranslation();
  const toast = useToast();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  async function handleLogout() {
    setIsLoggingOut(true);
    try {
      await logout();
      onLoggedOut();
      toast.success(t('auth.loggedOut'));
    } catch {
      toast.danger(t('common.unexpectedError'));
      setIsLoggingOut(false);
    }
  }

  return (
    <div className="logout-button">
      <Button variant="secondary" size="sm" icon={LogOut} loading={isLoggingOut} onClick={handleLogout}>
        {t('auth.logout')}
      </Button>
    </div>
  );
}
