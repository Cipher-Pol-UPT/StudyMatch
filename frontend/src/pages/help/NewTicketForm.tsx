import { Send } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Banner, Button, Select, Textarea, TextField, useFormFeedback } from '../../studymatch-ui';
import { TicketCategory } from '../../types/enums';
import type { OpenTicketRequest } from '../../types/tickets';

// as the backend allows (Ticket.MAX_SUBJECT_LENGTH, MAX_DESCRIPTION_LENGTH)
export const SUBJECT_MAX = 100;
export const TEXT_MAX = 2000;

export interface NewTicketFormProps {
  onSubmit: (ticket: OpenTicketRequest) => Promise<void>;
  onCancel: () => void;
}

// what it's about, in a line and in full; the category decides who answers
export function NewTicketForm({ onSubmit, onCancel }: NewTicketFormProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback();
  const [category, setCategory] = useState<TicketCategory | ''>('');
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const ready = category !== '' && subject.trim() !== '' && description.trim() !== '';

  async function save() {
    if (category === '') return;
    await onSubmit({ category, subject: subject.trim(), description: description.trim() });
  }

  return (
    <form className="help-form" noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(save); }}>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <Select label={t('help.category')} value={category} placeholder={t('help.pickCategory')} required disabled={feedback.busy}
        hint={category === 'ACCOUNT' ? t('help.accountHint') : undefined} error={feedback.fieldError('category')}
        options={TicketCategory.map(value => ({ value, label: t(`help.categories.${value}`) }))}
        onChange={event => setCategory(event.target.value as TicketCategory)} />
      <TextField label={t('help.subject')} value={subject} maxLength={SUBJECT_MAX} required disabled={feedback.busy}
        placeholder={t('help.subjectPlaceholder')} error={feedback.fieldError('subject')} onChange={event => setSubject(event.target.value)} />
      <Textarea label={t('help.description')} value={description} maxLength={TEXT_MAX} showCount rows={6} required disabled={feedback.busy}
        placeholder={t('help.descriptionPlaceholder')} error={feedback.fieldError('description')}
        onChange={event => setDescription(event.target.value)} />
      <div className="help-form__actions">
        <Button variant="ghost" onClick={onCancel} disabled={feedback.busy}>{t('common.cancel')}</Button>
        <Button type="submit" icon={Send} loading={feedback.busy} disabled={!ready}>{t('help.send')}</Button>
      </div>
    </form>
  );
}
