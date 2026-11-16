import { useTranslation } from 'react-i18next';
import type { FormFeedbackProps } from '../../../types/forms';
import { Dialog } from '../../components/overlay/Dialog';
import { FormFields } from '../FormFields';

// title and submitLabel are i18n keys; name fills the field when an existing category is renamed
export interface CategoryDialogProps extends FormFeedbackProps {
  open: boolean; title: string; submitLabel: string; name?: string;
  onClose: () => void; onSubmit: (name: string) => Promise<void>;
}
export function CategoryDialog({ open, title, submitLabel, name, onClose, onSubmit, fieldErrors }: CategoryDialogProps) {
  const { t } = useTranslation();
  if (!open) return null;
  return <Dialog open={open} onClose={onClose} title={t(title)}>
    <FormFields fields={[{ name: 'name', label: 'fields.name', value: name }]} submitLabel={submitLabel} fieldErrors={fieldErrors}
      onCancel={onClose} onSubmit={v => onSubmit(v.name.trim())} />
  </Dialog>;
}
