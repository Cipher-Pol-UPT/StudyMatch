import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { logout } from '../../api/auth';
import { isBackendError } from '../../api/errors';
import { useToast } from '../../studymatch-ui';

// stays logged in if the server didn't end the session, unless it had already expired
export function useSignOut(onSignedOut: () => void) {
  const { t } = useTranslation();
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
    onSignedOut();
  }

  return { signOut, busy };
}
