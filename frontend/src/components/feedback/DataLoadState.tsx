import { RefreshCw, TriangleAlert } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { isBackendError } from '../../api/errors';
import { Button, EmptyState, Spinner } from '../../studymatch-ui';

export function DataLoadState({ error, retry }: { error?: unknown; retry?: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="role-load" aria-live="polite">
      {error === undefined ? <Spinner /> : (
        <EmptyState
          icon={TriangleAlert}
          title={isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError')}
          action={retry && <Button variant="secondary" icon={RefreshCw} onClick={retry}>{t('profile.tryAgain')}</Button>}
        />
      )}
    </div>
  );
}
