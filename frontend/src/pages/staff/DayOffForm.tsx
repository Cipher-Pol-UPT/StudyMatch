import { CalendarOff } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError, type ErrorCode } from '../../api/errors';
import { Banner, Button, TextField, useFormFeedback } from '../../studymatch-ui';
import type { DayOffRequest } from '../../types/academicYears';

export const DAY_OFF_REASON_MAX = 100;

// the backend's rules for the days, each under the field it's about
const FIELD_OF: Partial<Record<ErrorCode, 'firstDay' | 'lastDay'>> = {
  DAY_OFF_ENDS_BEFORE_START: 'lastDay',
  DAY_OFF_OUTSIDE_SEMESTERS: 'firstDay',
  DAY_OFF_OVERLAPS: 'firstDay',
};

export interface DayOffFormProps {
  // the year's first and last day of classes, as yyyy-mm-dd, which the date fields stay between
  from: string;
  to: string;
  // a day picked on the calendar
  firstDay?: string;
  // the dates in the overlap error, in the page's format
  formatDay: (day: string) => string;
  onSubmit: (day: DayOffRequest) => Promise<void>;
  onCancel: () => void;
}

// one day, or a range when there's a last day, with its reason
export function DayOffForm({ from, to, firstDay: picked = '', formatDay, onSubmit, onCancel }: DayOffFormProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback();
  const [firstDay, setFirstDay] = useState(picked);
  const [lastDay, setLastDay] = useState('');
  const [reason, setReason] = useState('');
  const [codeError, setCodeError] = useState<{ field: 'firstDay' | 'lastDay'; message: string } | null>(null);
  // checked here first, the backend checks it again
  const backwards = firstDay !== '' && lastDay !== '' && lastDay < firstDay;

  const errorFor = (field: 'firstDay' | 'lastDay' | 'reason') => {
    if (field === 'lastDay' && backwards) return t('errors.DAY_OFF_ENDS_BEFORE_START');
    if (codeError?.field === field) return codeError.message;
    return feedback.fieldError(field);
  };

  async function save() {
    setCodeError(null);
    if (backwards) return;
    try {
      await onSubmit({ firstDay, lastDay: lastDay || null, reason: reason.trim() });
    } catch (error) {
      const field = isBackendError(error) ? FIELD_OF[error.code] : undefined;
      if (!field || !isBackendError(error)) throw error;
      const params = error.params ?? {};
      const dates = typeof params.firstDay === 'string' && typeof params.lastDay === 'string'
        ? { firstDay: formatDay(params.firstDay), lastDay: formatDay(params.lastDay) } : {};
      setCodeError({ field, message: t(`errors.${error.code}`, { ...params, ...dates }) });
    }
  }

  return (
    <form className="days-off__form" noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(save); }}>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <div className="days-off__dates">
        <TextField label={t('daysOff.firstDay')} type="date" value={firstDay} min={from} max={to} required disabled={feedback.busy}
          error={errorFor('firstDay')} onChange={event => setFirstDay(event.target.value)} />
        <TextField label={t('daysOff.lastDay')} type="date" value={lastDay} min={firstDay || from} max={to} disabled={feedback.busy}
          hint={t('daysOff.lastDayHint')} error={errorFor('lastDay')} onChange={event => setLastDay(event.target.value)} />
      </div>
      <TextField label={t('daysOff.reason')} value={reason} maxLength={DAY_OFF_REASON_MAX} placeholder={t('daysOff.reasonPlaceholder')}
        disabled={feedback.busy} error={errorFor('reason')} onChange={event => setReason(event.target.value)} />
      <div className="days-off__actions">
        <Button variant="ghost" onClick={onCancel} disabled={feedback.busy}>{t('common.cancel')}</Button>
        <Button type="submit" icon={CalendarOff} loading={feedback.busy} disabled={!firstDay || !reason.trim() || backwards}>
          {t('daysOff.save')}
        </Button>
      </div>
    </form>
  );
}
