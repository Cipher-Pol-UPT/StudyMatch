import { Download } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { exportCertificate } from '../../api/certificates';
import { Banner, Button, Checkbox, Dialog, Segmented, useFormFeedback, useToast, type AchievementId, type AchievementState } from '../../studymatch-ui';
import type { ExportCertificateRequest } from '../../types/certificates';

type Language = ExportCertificateRequest['language'];

export interface ExportCertificateDialogProps {
  open: boolean;
  earned: AchievementState[];
  // the ones ticked when it opens
  chosen: AchievementId[];
  onClose: () => void;
}

export function ExportCertificateDialog({ open, earned, chosen, onClose }: ExportCertificateDialogProps) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onClose={onClose} size="sm" title={t('achievements.export')} description={t('achievements.exportDescription')}>
      {/* a new form every time it opens, so the ticks start from what was clicked */}
      {open && <ExportCertificateForm earned={earned} chosen={chosen} onClose={onClose} />}
    </Dialog>
  );
}

function ExportCertificateForm({ earned, chosen, onClose }: Omit<ExportCertificateDialogProps, 'open'>) {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const feedback = useFormFeedback();
  const [selected, setSelected] = useState<AchievementId[]>(chosen);
  const [language, setLanguage] = useState<Language>(i18n.language.startsWith('pt') ? 'pt' : 'en');

  const toggle = (id: AchievementId, checked: boolean) =>
    setSelected(current => checked ? [...current, id] : current.filter(other => other !== id));

  async function download() {
    const pdf = await exportCertificate({ achievements: selected, language });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(pdf);
    link.download = selected.length === 1 ? 'certificate.pdf' : 'achievements.pdf';
    link.click();
    URL.revokeObjectURL(link.href);
    toast.success(t('achievements.downloaded'));
    onClose();
  }

  return (
    <form className="export-certificate" noValidate onSubmit={event => { event.preventDefault(); void feedback.submit(download); }}>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      <fieldset className="export-certificate__options" disabled={feedback.busy}>
        <legend>{t('achievements.chooseAchievements')}</legend>
        {earned.map(achievement => (
          <Checkbox key={achievement.id} checked={selected.includes(achievement.id)}
            onCheckedChange={checked => toggle(achievement.id, checked)}
            description={achievement.earnedAt && t('achievements.earnedOn', { date: new Date(achievement.earnedAt).toLocaleDateString(i18n.language, { day: 'numeric', month: 'short', year: 'numeric' }) })}>
            {t(`achievements.${achievement.id}.name`)}
          </Checkbox>
        ))}
      </fieldset>
      {selected.length === 0 && <p className="export-certificate__hint">{t('achievements.chooseOne')}</p>}
      <fieldset className="export-certificate__options" disabled={feedback.busy}>
        <legend>{t('achievements.pdfLanguage')}</legend>
        <Segmented<Language> label={t('achievements.pdfLanguage')} value={language} onValueChange={setLanguage}
          options={[{ value: 'en', label: t('achievements.english') }, { value: 'pt', label: t('achievements.portuguese') }]} />
      </fieldset>
      <div className="export-certificate__actions">
        <Button variant="secondary" onClick={onClose}>{t('ui.close')}</Button>
        <Button type="submit" icon={Download} loading={feedback.busy} disabled={selected.length === 0}>{t('achievements.download')}</Button>
      </div>
    </form>
  );
}
