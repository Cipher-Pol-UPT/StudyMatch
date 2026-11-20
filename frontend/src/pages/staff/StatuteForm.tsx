import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError } from '../../api/errors';
import { Banner, Button, Select, TextField, useFormFeedback } from '../../studymatch-ui';
import type { StatuteRequest } from '../../types/studentDirectory';
import { StatuteType } from '../../types/enums';

const dateInput = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export interface StatuteFormProps {
  onSubmit: (statute: StatuteRequest) => Promise<void>;
  onCancel: () => void;
  today?: Date;
}

// add or replace: it starts today, so only the type and the end date are asked
export function StatuteForm({ onSubmit, onCancel, today = new Date() }: StatuteFormProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback();
  const [type, setType] = useState('');
  const [endDate, setEndDate] = useState('');
  // the issue wants this one under the end date, the other errors stay in the banner
  const [pastDate, setPastDate] = useState(false);

  async function save() {
    setPastDate(false);
    try {
      await onSubmit({ type: (type || null) as StatuteRequest['type'], endDate: endDate || null });
    } catch (error) {
      if (isBackendError(error) && error.code === 'STATUTE_END_DATE_IN_PAST') setPastDate(true);
      else throw error;
    }
  }

  return (
    <form className="directory-statute-form" noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(save); }}>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <Select label={t('directory.statuteType')} value={type} placeholder={t('directory.chooseStatute')} disabled={feedback.busy}
        error={feedback.fieldError('type')} options={StatuteType.map(value => ({ value, label: t(`statutes.${value}`) }))}
        onChange={event => setType(event.target.value)} />
      <TextField label={t('directory.statuteEnd')} type="date" value={endDate} min={dateInput(today)} disabled={feedback.busy}
        error={pastDate ? t('errors.STATUTE_END_DATE_IN_PAST') : feedback.fieldError('endDate')}
        onChange={event => setEndDate(event.target.value)} />
      <div className="directory-statute-form__actions">
        <Button variant="secondary" onClick={onCancel} disabled={feedback.busy}>{t('common.cancel')}</Button>
        <Button type="submit" loading={feedback.busy}>{t('common.save')}</Button>
      </div>
    </form>
  );
}
