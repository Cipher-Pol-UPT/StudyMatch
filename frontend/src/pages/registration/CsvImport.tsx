import { Download, FileUp, Send, Upload } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { FieldError } from '../../api/errors';
import { downloadImportTemplate, importStudents, previewImport } from '../../api/students';
import { Banner, Button, Card, Dialog, FileDrop, Spinner, StatusPill, Table, useFormFeedback, type StatusTone, type TableColumn } from '../../studymatch-ui';
import type { ImportPreviewRow, ImportResultRow, ImportStatus } from '../../types/registration';
import { ResendCredentials } from './ResendCredentials';

const RESULT_TONES: Record<ImportStatus, StatusTone> = { CREATED: 'success', EMAIL_NOT_SENT: 'warning', REJECTED: 'danger' };

export function CsvImport() {
  const { t, i18n } = useTranslation();
  const feedback = useFormFeedback();
  const [preview, setPreview] = useState<ImportPreviewRow[] | null>(null);
  const [results, setResults] = useState<ImportResultRow[] | null>(null);
  const [resendFor, setResendFor] = useState<{ number: number; personalEmail: string } | null>(null);
  const validRows = preview?.filter(row => row.errors.length === 0) ?? [];

  function describe(error: FieldError) {
    const key = i18n.exists(`validation.${error.code}`) ? `validation.${error.code}` : `errors.${error.code}`;
    return `${t(`fields.${error.field}`)}: ${t(key, error.params ?? {})}`;
  }

  async function saveTemplate() {
    const template = await downloadImportTemplate();
    const link = document.createElement('a');
    link.href = URL.createObjectURL(template);
    link.download = 'students.csv';
    link.click();
    URL.revokeObjectURL(link.href);
  }

  function readFile(files: File[]) {
    const [file] = files;
    if (file) void feedback.submit(async () => setPreview(await previewImport(file)));
  }

  function confirmImport() {
    void feedback.submit(async () => setResults(await importStudents(validRows)));
  }

  function startOver() {
    setPreview(null);
    setResults(null);
  }

  if (results && preview) {
    const studentOf = (row: number) => preview.find(previewRow => previewRow.row === row)?.student;
    const columns: TableColumn<ImportResultRow>[] = [
      { key: 'row', header: t('registration.row'), render: row => <span className="sm-num">{row.row}</span> },
      { key: 'name', header: t('fields.name'), render: row => studentOf(row.row)?.name },
      { key: 'status', header: t('registration.status'), render: row => <StatusPill tone={RESULT_TONES[row.status]}>{t(`registration.results.${row.status}`)}</StatusPill> },
      {
        key: 'details', header: t('registration.details'), render: row => row.status === 'REJECTED'
          ? <ul className="registration__errors">{row.errors.map(error => <li key={error.field + error.code}>{describe(error)}</li>)}</ul>
          : <div className="registration__account">
              <span>{row.institutionalEmail}</span>
              {row.status === 'EMAIL_NOT_SENT' && row.number !== null && (
                <Button size="sm" variant="secondary" icon={Send}
                  onClick={() => setResendFor({ number: row.number!, personalEmail: studentOf(row.row)?.personalEmail ?? '' })}>
                  {t('registration.resend')}
                </Button>
              )}
            </div>,
      },
    ];
    const count = (status: ImportStatus) => results.filter(row => row.status === status).length;
    return (
      <Card className="registration__card">
        <h2 className="registration__title">{t('registration.importDone')}</h2>
        <p className="registration__note">{t('registration.importSummary', { created: count('CREATED'), notSent: count('EMAIL_NOT_SENT'), rejected: count('REJECTED') })}</p>
        <div className="registration__table"><Table columns={columns} rows={results} rowKey={row => row.row} /></div>
        <Button icon={FileUp} onClick={startOver}>{t('registration.importAnother')}</Button>
        <Dialog open={resendFor !== null} onClose={() => setResendFor(null)} title={t('registration.resend')}>
          {resendFor && <ResendCredentials studentNumber={resendFor.number} personalEmail={resendFor.personalEmail} onSent={() => setResendFor(null)} />}
        </Dialog>
      </Card>
    );
  }

  if (preview) {
    const columns: TableColumn<ImportPreviewRow>[] = [
      { key: 'row', header: t('registration.row'), render: row => <span className="sm-num">{row.row}</span> },
      { key: 'name', header: t('fields.name'), render: row => row.student.name },
      { key: 'personalEmail', header: t('fields.personalEmail'), render: row => row.student.personalEmail },
      { key: 'degree', header: t('fields.degreeProgramme'), render: row => [row.student.degreeProgramme, row.student.curricularYear].filter(Boolean).join(' · ') },
      {
        key: 'status', header: t('registration.status'), render: row => row.errors.length === 0
          ? <StatusPill tone="success">{t('registration.ready')}</StatusPill>
          : <ul className="registration__errors">{row.errors.map(error => <li key={error.field + error.code}>{describe(error)}</li>)}</ul>,
      },
    ];
    return (
      <Card className="registration__card">
        <h2 className="registration__title">{t('registration.previewTitle')}</h2>
        <p className="registration__note">{t('registration.previewSummary', { valid: validRows.length, invalid: preview.length - validRows.length })}</p>
        {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
        <div className="registration__table"><Table columns={columns} rows={preview} rowKey={row => row.row} /></div>
        <div className="registration__actions">
          <Button icon={Upload} loading={feedback.busy} disabled={validRows.length === 0} onClick={confirmImport}>
            {t('registration.importValid', { count: validRows.length })}
          </Button>
          <Button variant="secondary" disabled={feedback.busy} onClick={startOver}>{t('common.cancel')}</Button>
        </div>
      </Card>
    );
  }

  return (
    <Card className="registration__card">
      <h2 className="registration__title">{t('registration.csvTitle')}</h2>
      <p className="registration__note">{t('registration.csvRules')}</p>
      <Button variant="secondary" icon={Download} disabled={feedback.busy} onClick={() => void feedback.submit(saveTemplate)}>{t('registration.downloadTemplate')}</Button>
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      {feedback.busy
        ? <div className="registration__loading"><Spinner /></div>
        : <FileDrop accept=".csv,text/csv" onFiles={readFile} hint={t('registration.csvHint')} preview={false} />}
    </Card>
  );
}
