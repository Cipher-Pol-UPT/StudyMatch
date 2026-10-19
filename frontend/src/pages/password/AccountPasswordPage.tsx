import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { changePassword } from '../../api/auth';
import { Card, ChangePasswordForm, useToast } from '../../studymatch-ui';
import type { ChangePasswordInput } from '../../types/forms';
import { PASSWORD_RULES } from './passwordRules';
import '../registration/registration.css';

export function AccountPasswordPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const navigate = useNavigate();

  async function saveNewPassword(change: ChangePasswordInput) {
    await changePassword(change);
    toast.success(t('password.changed'));
    navigate('/');
  }

  return (
    <div className="registration">
      <Card className="registration__card">
        <ChangePasswordForm passwordRules={PASSWORD_RULES} onSubmit={saveNewPassword} onCancel={() => navigate('/')} />
      </Card>
    </div>
  );
}
