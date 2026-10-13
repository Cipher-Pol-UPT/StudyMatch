import { useId,useState,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { FieldError } from '../../api/errors';
import { Button } from '../components/button/Button';
import { PasswordField } from '../components/field/PasswordField';
import { TextField } from '../components/field/TextField';
import { Banner } from '../components/overlay/Banner';
import { useFormFeedback } from '../utils/useFormFeedback';

interface InputField { name: string; label: string; type?: string; value?: string; options?: { value: string; label: string }[] }
export interface FormFieldsProps {
  title?: ReactNode; fields: InputField[]; submitLabel: string;
  onSubmit: (values: Record<string, string>) => Promise<void>;
  fieldErrors?: FieldError[]; onCancel?: () => void;
}

export function FormFields({ title, fields, submitLabel, onSubmit, fieldErrors, onCancel }: FormFieldsProps) {
  const { t } = useTranslation();
  const id = useId();
  const [values, setValues] = useState<Record<string, string>>(() => Object.fromEntries(fields.map(f => [f.name, f.value ?? ''])));
  const feedback = useFormFeedback(fieldErrors);
  return <form className="sm-auth__form" noValidate onSubmit={event => {
    event.preventDefault(); void feedback.submit(() => onSubmit(values));
  }}>
    {title && <h2 className="sm-auth__title">{title}</h2>}
    {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
    {fields.map(field => field.options ? <div key={field.name} className="sm-field">
      <label htmlFor={`${id}-${field.name}`}>{t(field.label)}</label>
      <select id={`${id}-${field.name}`} value={values[field.name]} disabled={feedback.busy}
        aria-invalid={!!feedback.fieldError(field.name)} aria-describedby={feedback.fieldError(field.name) ? `${id}-${field.name}-error` : undefined}
        onChange={event => setValues(v => ({ ...v, [field.name]: event.target.value }))}>
        {field.options.map(option => <option key={option.value} value={option.value}>{t(option.label)}</option>)}
      </select>
      {feedback.fieldError(field.name) && <p id={`${id}-${field.name}-error`} role="alert">{feedback.fieldError(field.name)}</p>}
    </div> : field.type === 'password' ? <PasswordField key={field.name} label={t(field.label)}
      value={values[field.name]} disabled={feedback.busy} error={feedback.fieldError(field.name)}
      onChange={event => setValues(v => ({ ...v, [field.name]: event.target.value }))} />
      : <TextField key={field.name} label={t(field.label)} type={field.type ?? 'text'} value={values[field.name]}
        disabled={feedback.busy} error={feedback.fieldError(field.name)}
        onChange={event => setValues(v => ({ ...v, [field.name]: event.target.value }))} />)}
    <Button type="submit" loading={feedback.busy}>{t(submitLabel)}</Button>
    {onCancel && <Button variant="secondary" onClick={onCancel} disabled={feedback.busy}>{t('common.cancel')}</Button>}
  </form>;
}
