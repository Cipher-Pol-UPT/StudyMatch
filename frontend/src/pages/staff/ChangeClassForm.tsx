import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Banner, Button, Select, useFormFeedback } from '../../studymatch-ui';

export interface ChangeClassFormProps {
  // the edition's other classes, the current one isn't offered
  classSections: string[];
  onSubmit: (classSection: string) => Promise<void>;
  onCancel: () => void;
}

// CLASS_SECTION_NOT_IN_SUBJECT_EDITION and the rest go in the banner
export function ChangeClassForm({ classSections, onSubmit, onCancel }: ChangeClassFormProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback();
  const [classSection, setClassSection] = useState('');

  return (
    <form className="directory-statute-form" noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(() => onSubmit(classSection)); }}>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <Select label={t('directory.newClass')} value={classSection} placeholder={t('directory.chooseClass')} disabled={feedback.busy}
        error={feedback.fieldError('classSection')} options={classSections.map(value => ({ value, label: value }))}
        onChange={event => setClassSection(event.target.value)} />
      <div className="directory-statute-form__actions">
        <Button variant="secondary" onClick={onCancel} disabled={feedback.busy}>{t('common.cancel')}</Button>
        <Button type="submit" loading={feedback.busy} disabled={!classSection}>{t('directory.changeClass')}</Button>
      </div>
    </form>
  );
}
