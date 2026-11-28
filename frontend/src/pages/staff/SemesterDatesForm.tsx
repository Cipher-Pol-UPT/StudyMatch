import { CalendarCheck } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError } from '../../api/errors';
import { Banner, Button, TextField, useFormFeedback } from '../../studymatch-ui';
import type { SemesterDatesData } from '../../types/academicYears';
import { FIELDS, problemOf, yearLimits, type DateField } from './semesterDates';

export interface SemesterDatesFormProps {
  startYear: number;
  // the dates it starts from: the year's own, or the suggestion
  initial: SemesterDatesData;
  submitLabel: string;
  // the dates in the messages, in the page's format
  formatDay: (day: string) => string;
  onSubmit: (dates: SemesterDatesData) => Promise<void>;
  onCancel: () => void;
}

// the two semesters' first and last days
export function SemesterDatesForm({ startYear, initial, submitLabel, formatDay, onSubmit, onCancel }: SemesterDatesFormProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback();
  const [dates, setDates] = useState(initial);
  const [backendError, setBackendError] = useState<{ field: DateField; message: string } | null>(null);
  const { from, to } = yearLimits(startYear);
  const problem = problemOf(dates, startYear);
  const complete = FIELDS.every(field => dates[field] !== '');

  const messageOf = (code: string, params: Record<string, unknown> = {}) => t(`errors.${code}`, {
    ...params,
    ...(typeof params.from === 'string' ? { from: formatDay(params.from) } : {}),
    ...(typeof params.to === 'string' ? { to: formatDay(params.to) } : {}),
  });
  const errorFor = (field: DateField) => {
    if (problem?.field === field) return messageOf(problem.code, problem.params);
    if (backendError?.field === field) return backendError.message;
    return feedback.fieldError(field);
  };

  async function save() {
    setBackendError(null);
    if (problem || !complete) return;
    try {
      await onSubmit(dates);
    } catch (error) {
      if (!isBackendError(error)) throw error;
      // the same rules as above, in case the backend sees what this page didn't
      const field: DateField | undefined = error.code === 'SEMESTER_OUTSIDE_YEAR' ? 'semester1Start'
        : error.code === 'SEMESTER_ENDS_BEFORE_START' ? (error.params?.semester === 2 ? 'semester2End' : 'semester1End')
        : error.code === 'SEMESTERS_OVERLAP' ? 'semester2Start' : undefined;
      if (!field) throw error;
      setBackendError({ field, message: messageOf(error.code, error.params ?? {}) });
    }
  }

  const date = (field: DateField) => (
    <TextField label={t(`semesters.${field}`)} type="date" value={dates[field]} min={from} max={to} required disabled={feedback.busy}
      error={errorFor(field)} onChange={event => {
        setBackendError(null);
        setDates(current => ({ ...current, [field]: event.target.value }));
      }} />
  );

  return (
    <form className="semesters__form" noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(save); }}>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <div className="semesters__pair">
        {date('semester1Start')}
        {date('semester1End')}
        {date('semester2Start')}
        {date('semester2End')}
      </div>
      <div className="days-off__actions">
        <Button variant="ghost" onClick={onCancel} disabled={feedback.busy}>{t('common.cancel')}</Button>
        <Button type="submit" icon={CalendarCheck} loading={feedback.busy} disabled={!complete || problem !== null}>{submitLabel}</Button>
      </div>
    </form>
  );
}
