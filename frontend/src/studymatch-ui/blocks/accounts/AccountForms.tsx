import { useTranslation } from 'react-i18next';
import type { AccountStatus } from '../../../types/enums';
import type { CreateAccountInput,FormFeedbackProps,RegisterStudentInput } from '../../../types/forms';
import { Dialog } from '../../components/overlay/Dialog';
import { FormFields } from '../FormFields';
export interface CreateAccountFormProps extends FormFeedbackProps { onSubmit: (input: CreateAccountInput) => Promise<void> }
export function CreateAccountForm({ onSubmit, fieldErrors }: CreateAccountFormProps) {
  const { t } = useTranslation();
  return <FormFields title={t('accounts.create')} fields={[
    { name: 'name', label: 'fields.name' }, { name: 'personalEmail', label: 'fields.personalEmail', type: 'email' },
    { name: 'role', label: 'fields.role', value: 'STAFF', options: [{value:'STAFF',label:'roles.STAFF'},{value:'TEACHER',label:'roles.TEACHER'}] },
  ]} submitLabel="common.submit" fieldErrors={fieldErrors}
    onSubmit={v => onSubmit({ name: v.name, personalEmail: v.personalEmail, role: v.role as CreateAccountInput['role'] })} />;
}
export interface RegisterStudentFormProps extends FormFeedbackProps { onSubmit: (input: RegisterStudentInput) => Promise<void> }
export function RegisterStudentForm({ onSubmit, fieldErrors }: RegisterStudentFormProps) {
  const { t } = useTranslation();
  return <FormFields title={t('accounts.registerStudent')} fields={[
    { name: 'name', label: 'fields.name' }, { name: 'studentNumber', label: 'fields.studentNumber' },
    { name: 'personalEmail', label: 'fields.personalEmail', type: 'email' },
    { name: 'programme', label: 'fields.programme' }, { name: 'curricularYear', label: 'fields.curricularYear', type: 'number' },
  ]} submitLabel="common.submit" fieldErrors={fieldErrors}
    onSubmit={v => onSubmit({ name: v.name, studentNumber: v.studentNumber, personalEmail: v.personalEmail, programme: v.programme, curricularYear: Number(v.curricularYear) })} />;
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
