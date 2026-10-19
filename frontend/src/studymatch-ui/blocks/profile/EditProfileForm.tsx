import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Language } from '../../../types/enums';
import { Language as LANGUAGE_CODES } from '../../../types/enums';
import type { FormFeedbackProps } from '../../../types/forms';
import { Button } from '../../components/button/Button';
import { Chip } from '../../components/chip/Chip';
import { TagInput } from '../../components/chip/TagInput';
import { Textarea } from '../../components/field/Textarea';
import { Banner } from '../../components/overlay/Banner';
import { cx } from '../../utils/cx';
import { useFormFeedback } from '../../utils/useFormFeedback';
import type { StudentProfile } from './ProfileView';
import './profile.css';

export interface ProfileEdits { aboutMe: string; skills: string[]; interests: string[]; languages: Language[] }
export interface EditProfileFormProps extends FormFeedbackProps {
  profile: StudentProfile; onSubmit: (edits: ProfileEdits) => Promise<void>; onCancel?: () => void;
}

export function EditProfileForm({ profile, onSubmit, onCancel, fieldErrors }: EditProfileFormProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback(fieldErrors);
  const languagesErrorId = useId();
  const [aboutMe, setAboutMe] = useState(profile.aboutMe ?? '');
  const [skills, setSkills] = useState(profile.skills);
  const [interests, setInterests] = useState(profile.interests);
  const [languages, setLanguages] = useState(profile.languages);
  const languagesError = feedback.fieldError('languages');

  function toggleLanguage(code: Language) {
    setLanguages(list => list.includes(code) ? list.filter(item => item !== code) : [...list, code]);
  }

  return (
    <form
      className="sm-edit-profile__form"
      noValidate
      onSubmit={event => {
        event.preventDefault();
        void feedback.submit(() => onSubmit({ aboutMe, skills, interests, languages }));
      }}
    >
      <div className="sm-edit-profile__fields">
        <Textarea label={t('fields.aboutMe')} value={aboutMe} onChange={event => setAboutMe(event.target.value)} error={feedback.fieldError('aboutMe')} />
        <TagInput label={t('fields.skills')} value={skills} onValueChange={setSkills} error={feedback.fieldError('skills')} />
        <TagInput label={t('fields.interests')} value={interests} onValueChange={setInterests} error={feedback.fieldError('interests')} />
        <fieldset
          className={cx('sm-edit-profile__languages', !!languagesError && 'sm-edit-profile__languages--error')}
          aria-describedby={languagesError ? languagesErrorId : undefined}
        >
          <legend className="sm-edit-profile__legend">{t('fields.languages')}</legend>
          <div className="sm-chip-group">
            {LANGUAGE_CODES.map(code => (
              <Chip key={code} selected={languages.includes(code)} onClick={() => toggleLanguage(code)}>
                {t(`languages.${code}`)}
              </Chip>
            ))}
          </div>
          {languagesError && <p id={languagesErrorId} className="sm-edit-profile__error" role="alert">{languagesError}</p>}
        </fieldset>
      </div>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <div className="sm-edit-profile__actions">
        {onCancel && <Button variant="secondary" onClick={onCancel} disabled={feedback.busy}>{t('common.cancel')}</Button>}
        <Button type="submit" loading={feedback.busy}>{t('common.save')}</Button>
      </div>
    </form>
  );
}
