import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { EmployeeRole } from '../../../types/accounts';
import type { AccountStatus } from '../../../types/enums';
import type { CreateAccountInput,FormFeedbackProps } from '../../../types/forms';
import type { StudentRegistration } from '../../../types/registration';
import type { DegreeData } from '../../../types/studentDirectory';
import { Button } from '../../components/button/Button';
import { Select } from '../../components/field/Select';
import { TextField } from '../../components/field/TextField';
import { Banner } from '../../components/overlay/Banner';
import { Dialog } from '../../components/overlay/Dialog';
import { Segmented } from '../../components/segmented/Segmented';
import { useFormFeedback } from '../../utils/useFormFeedback';
import { FormFields } from '../FormFields';
import './accounts.css';
export interface CreateAccountFormProps extends FormFeedbackProps {
  domain: string; onSubmit: (input: CreateAccountInput) => Promise<void>;
}
export function CreateAccountForm({ domain, onSubmit, fieldErrors }: CreateAccountFormProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback(fieldErrors);
  const [type, setType] = useState<EmployeeRole>('STAFF');
  const [name, setName] = useState('');
  const [personalEmail, setPersonalEmail] = useState('');
  const [username, setUsername] = useState('');
  return <form className="sm-account-form" noValidate onSubmit={event => {
    event.preventDefault(); void feedback.submit(() => onSubmit({ type, name, personalEmail, username }));
  }}>
    {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
    <div className="sm-account-form__type">
      <span className="sm-account-form__label">{t('fields.accountType')}</span>
      <Segmented<EmployeeRole> label={t('fields.accountType')} value={type} onValueChange={setType}
        options={[{ value: 'STAFF', label: t('roles.STAFF') }, { value: 'TEACHER', label: t('roles.TEACHER') }]} />
    </div>
    <TextField label={t('fields.name')} autoComplete="off" value={name} disabled={feedback.busy}
      error={feedback.fieldError('name')} onChange={event => setName(event.target.value)} />
    <TextField label={t('fields.personalEmail')} type="email" autoComplete="off" value={personalEmail} disabled={feedback.busy}
      error={feedback.fieldError('personalEmail')} onChange={event => setPersonalEmail(event.target.value)} />
    <TextField label={t('fields.username')} autoComplete="off" autoCapitalize="none" spellCheck={false} value={username}
      suffix={<span className="sm-account-form__domain">@{domain}</span>} hint={t('accounts.usernameHint')} disabled={feedback.busy}
      error={feedback.fieldError('username')} onChange={event => setUsername(event.target.value)} />
    <Button type="submit" size="lg" loading={feedback.busy}>{t('accounts.createSubmit')}</Button>
  </form>;
}
export interface RegisterStudentFormProps extends FormFeedbackProps {
  degrees: DegreeData[]; onSubmit: (input: StudentRegistration) => Promise<void>;
}
export function RegisterStudentForm({ degrees, onSubmit, fieldErrors }: RegisterStudentFormProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback(fieldErrors);
  const [name, setName] = useState('');
  const [personalEmail, setPersonalEmail] = useState('');
  const [degreeProgramme, setDegreeProgramme] = useState('');
  const [curricularYear, setCurricularYear] = useState('');
  const years = degrees.find(degree => degree.name === degreeProgramme)?.curricularYears ?? 0;
  return <form className="sm-account-form" noValidate onSubmit={event => {
    event.preventDefault();
    void feedback.submit(() => onSubmit({ name, personalEmail, degreeProgramme, curricularYear: curricularYear ? Number(curricularYear) : null }));
  }}>
    {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
    <TextField label={t('fields.name')} autoComplete="off" value={name} disabled={feedback.busy}
      error={feedback.fieldError('name')} onChange={event => setName(event.target.value)} />
    <TextField label={t('fields.personalEmail')} type="email" autoComplete="off" value={personalEmail} disabled={feedback.busy}
      error={feedback.fieldError('personalEmail')} onChange={event => setPersonalEmail(event.target.value)} />
    <div className="sm-account-form__row">
      <Select label={t('fields.degreeProgramme')} value={degreeProgramme} placeholder={t('registration.chooseDegree')} disabled={feedback.busy}
        error={feedback.fieldError('degreeProgramme')} options={degrees.map(degree => ({ value: degree.name, label: degree.name }))}
        onChange={event => { setDegreeProgramme(event.target.value); setCurricularYear(''); }} />
      <Select label={t('fields.curricularYear')} value={curricularYear} placeholder={t('registration.chooseYear')} disabled={feedback.busy || years === 0}
        error={feedback.fieldError('curricularYear')}
        options={Array.from({ length: years }, (_, index) => ({ value: String(index + 1), label: t('registration.year', { year: index + 1 }) }))}
        onChange={event => setCurricularYear(event.target.value)} />
    </div>
    <Button type="submit" size="lg" loading={feedback.busy}>{t('registration.submit')}</Button>
  </form>;
}
export interface BlockAccountDialogProps extends FormFeedbackProps {
  open: boolean; user: { id: string; name: string }; status: AccountStatus;
  onClose: () => void; onSubmit: (input: { userId: string; reason: string }) => Promise<void>;
}
export function BlockAccountDialog({ open, user, status, onClose, onSubmit, fieldErrors }: BlockAccountDialogProps) {
  const { t } = useTranslation();
  if (!open) return null;
  return <Dialog open={open} onClose={onClose} title={t('accounts.changeStatus')}>
    <p>{user.name} · {t(`accountStatuses.${status}`)}</p>
    <FormFields fields={[{name:'reason',label:'fields.reason'}]} submitLabel="common.confirm" fieldErrors={fieldErrors}
      onCancel={onClose} onSubmit={v => onSubmit({ userId: user.id, reason: v.reason })} />
  </Dialog>;
}
