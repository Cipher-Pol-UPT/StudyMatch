import { BookOpen, Download } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { downloadEnrolmentTemplate, enrolStudents, getCurrentEditions, importEnrolments } from '../../api/enrolments';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, Card, EmptyState, FileDrop, Segmented, Select, Spinner, useFormFeedback } from '../../studymatch-ui';
import type { EnrolmentResultData } from '../../types/enrolments';
import { EnrolmentPicker } from './EnrolmentPicker';
import { EnrolmentResults } from './EnrolmentResults';
import './enrolments.css';

type EnrolmentMode = 'choose' | 'csv';

export function StaffEnrolmentsPage() {
  const { t } = useTranslation();
  const editions = useRemoteData('current-editions', getCurrentEditions);
  const [editionId, setEditionId] = useState('');
  const [classSection, setClassSection] = useState('');
  const [mode, setMode] = useState<EnrolmentMode>('choose');
  const [results, setResults] = useState<EnrolmentResultData[] | null>(null);
  const csv = useFormFeedback();

  // the first edition and its first class are picked until Staff chooses
  const edition = editions.data?.find(item => String(item.id) === editionId) ?? editions.data?.[0];
  const section = edition?.classSections.includes(classSection) ? classSection : edition?.classSections[0] ?? '';

  async function enrol(studentNumbers: number[]) {
    if (edition) setResults(await enrolStudents(edition.id, { classSection: section, studentNumbers }));
  }

  async function saveTemplate() {
    const template = await downloadEnrolmentTemplate();
    const link = document.createElement('a');
    link.href = URL.createObjectURL(template);
    link.download = 'enrolments.csv';
    link.click();
    URL.revokeObjectURL(link.href);
  }

  function readFile([file]: File[]) {
    if (file && edition) void csv.submit(async () => setResults(await importEnrolments(edition.id, section, file)));
  }

  return (
    <div className="role-page directory-page">
      <header>
        <h1 className="sm-h2">{t('workspace.sections.enrolments')}</h1>
        <p className="sm-caption">{t('enrolments.intro')}</p>
      </header>
      {editions.loading || editions.error !== undefined ? (
        <DataLoadState error={editions.error} retry={editions.retry} />
      ) : !edition ? (
        <EmptyState icon={BookOpen} title={t('enrolments.noEditions')} body={t('enrolments.noEditionsBody')} />
      ) : results ? (
        <EnrolmentResults results={results} onDone={() => setResults(null)} />
      ) : (
        <>
          <div className="enrolments__target">
            <Select label={t('enrolments.edition')} value={String(edition.id)} onChange={event => setEditionId(event.target.value)}
              options={(editions.data ?? []).map(item => ({ value: String(item.id), label: `${item.subject.code} · ${item.subject.name}` }))} />
            <Select label={t('directory.class')} value={section} onChange={event => setClassSection(event.target.value)}
              options={edition.classSections.map(code => ({ value: code, label: code }))} />
          </div>
          <Segmented<EnrolmentMode> label={t('enrolments.how')} value={mode} onValueChange={setMode}
            options={[{ value: 'choose', label: t('enrolments.choose') }, { value: 'csv', label: t('registration.csvImport') }]} />
          {mode === 'choose' ? (
            <EnrolmentPicker key={`${edition.id}-${section}`} onEnrol={enrol} />
          ) : (
            <Card className="enrolments__card">
              <h2 className="enrolments__title">{t('enrolments.csvTitle')}</h2>
              <p className="enrolments__note">{t('enrolments.csvRules')}</p>
              <Button variant="secondary" icon={Download} disabled={csv.busy} onClick={() => void csv.submit(saveTemplate)}>{t('registration.downloadTemplate')}</Button>
              {csv.message && <Banner tone="danger">{csv.message}</Banner>}
              {csv.busy
                ? <div className="enrolments__loading"><Spinner /></div>
                : <FileDrop accept=".csv,text/csv" onFiles={readFile} hint={t('registration.csvHint')} preview={false} />}
            </Card>
          )}
        </>
      )}
    </div>
  );
}
