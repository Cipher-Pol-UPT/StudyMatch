import { useTranslation } from 'react-i18next';
import { changePassword, logout } from '../../api/auth';
import { AuthLayout, Button, ChangePasswordForm, ThemeSwitcher, useToast } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import type { ChangePasswordInput } from '../../types/forms';
import { PASSWORD_RULES } from './passwordRules';

export interface ChangePasswordPageProps {
  onPasswordChanged: (user: CurrentUser) => void;
  onLoggedOut: () => void;
}

export function ChangePasswordPage({ onPasswordChanged, onLoggedOut }: ChangePasswordPageProps) {
  const { t } = useTranslation();
  const toast = useToast();

  async function saveNewPassword(change: ChangePasswordInput) {
    const user = await changePassword(change);
    toast.success(t('password.changed'));
    onPasswordChanged(user);
  }

  function signOut() {
    logout().catch(() => undefined).finally(onLoggedOut);
  }

  return (
    <AuthLayout centerBrand hideArt hideFooter topRight={<ThemeSwitcher />}>
      <ChangePasswordForm
        showCurrentPassword={false}
        passwordRules={PASSWORD_RULES}
        title={t('password.firstTitle')}
        subtitle={t('password.firstSubtitle')}
        onSubmit={saveNewPassword}
      />
      <Button variant="ghost" block onClick={signOut}>{t('nav.logout')}</Button>
    </AuthLayout>
  );
}
