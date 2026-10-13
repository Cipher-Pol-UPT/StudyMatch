import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Language } from '../../../types/enums';
import { Language as LANGUAGE_CODES } from '../../../types/enums';
import type { FormFeedbackProps } from '../../../types/forms';
import { Button } from '../../components/button/Button';
import { TagInput } from '../../components/chip/TagInput';
import { Textarea } from '../../components/field/Textarea';
import { Banner } from '../../components/overlay/Banner';
import { useFormFeedback } from '../../utils/useFormFeedback';
import type { StudentProfile } from './ProfileView';
export interface ProfileEdits { aboutMe: string; skills: string[]; interests: string[]; languages: Language[] }
export interface EditProfileFormProps extends FormFeedbackProps {
  profile: StudentProfile; onSubmit: (edits: ProfileEdits) => Promise<void>; onCancel?: () => void;
}
export function EditProfileForm({ profile, onSubmit, onCancel, fieldErrors }: EditProfileFormProps) {
  const { t } = useTranslation(); const feedback = useFormFeedback(fieldErrors);
  const [aboutMe, setAboutMe] = useState(profile.aboutMe ?? '');
  const [skills, setSkills] = useState(profile.skills); const [interests, setInterests] = useState(profile.interests);
  const [languages, setLanguages] = useState(profile.languages);
  return <form noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(() => onSubmit({aboutMe,skills,interests,languages})); }}>
    <Textarea label={t('fields.aboutMe')} value={aboutMe} onChange={event => setAboutMe(event.target.value)} error={feedback.fieldError('aboutMe')} />
    <TagInput label={t('fields.skills')} value={skills} onValueChange={setSkills} error={feedback.fieldError('skills')} />
    <TagInput label={t('fields.interests')} value={interests} onValueChange={setInterests} error={feedback.fieldError('interests')} />
    <fieldset><legend>{t('fields.languages')}</legend>{LANGUAGE_CODES.map(code => <label key={code}>
      <input type="checkbox" checked={languages.includes(code)} onChange={event => setLanguages(list => event.target.checked ? [...list,code] : list.filter(item => item !== code))} />{t(`languages.${code}`)}
    </label>)}{feedback.fieldError('languages') && <p role="alert">{feedback.fieldError('languages')}</p>}</fieldset>
    {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
    <Button type="submit" loading={feedback.busy}>{t('common.save')}</Button>
    {onCancel && <Button onClick={onCancel}>{t('common.cancel')}</Button>}
  </form>;
}
