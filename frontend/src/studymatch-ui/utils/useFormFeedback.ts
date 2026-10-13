import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError,type BackendError,type FieldError } from '../../api/errors';

export function useFormFeedback(fieldErrors: FieldError[] = []) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<BackendError | null>(null);
  const [failed, setFailed] = useState(false);
  const fields = [...fieldErrors, ...(error?.fieldErrors ?? [])];
  const fieldError = (field: string) => fields.filter(e => e.field === field)
    .map(e => t(`validation.${e.code}`, { ...e.params, defaultValue: t('validation.INVALID') })).join(' ') || undefined;
  const submit = async (action: () => Promise<void>) => {
    setBusy(true); setError(null); setFailed(false);
    try { const result: unknown = await action(); if (isBackendError(result)) setError(result); }
    catch (value) { if (isBackendError(value)) setError(value); else setFailed(true); }
    finally { setBusy(false); }
  };
  return { busy, fieldError, submit,
    message: error ? t(`errors.${error.code}`, error.params ?? {}) : failed ? t('common.unexpectedError') : undefined,
  };
}
