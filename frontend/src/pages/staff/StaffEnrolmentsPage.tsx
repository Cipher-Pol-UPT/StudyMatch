import { Download } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { downloadEnrolmentTemplate, enrolStudents, importEnrolments } from '../../api/enrolments';
import { Banner, Button, Card, FileDrop, Segmented, Select, Spinner, useFormFeedback } from '../../studymatch-ui';
import type { EnrolmentResultData } from '../../types/enrolments';
import { CurrentEditionPage } from './CurrentEditionPage';
import { useCurrentEdition } from './useCurrentEdition';
import { EnrolmentPicker } from './EnrolmentPicker';
import { EnrolmentResults } from './EnrolmentResults';
import './enrolments.css';

type EnrolmentMode = 'choose' | 'csv';

export function StaffEnrolmentsPage() {
  const { t } = useTranslation();
  const current = useCurrentEdition();
  const { editions, edition, section } = current;
  const [mode, setMode] = useState<EnrolmentMode>('choose');
  const [results, setResults] = useState<EnrolmentResultData[] | null>(null);
  const csv = useFormFeedback();

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
    <CurrentEditionPage title={t('workspace.sections.enrolments')} intro={t('enrolments.intro')} emptyBody={t('enrolments.noEditionsBody')} current={current}>
      {chosen => results ? (
        <EnrolmentResults results={results} onDone={() => setResults(null)} />
      ) : (
        <>
          <div className="enrolments__target">
            <Select label={t('enrolments.edition')} value={String(chosen.id)} onChange={event => current.setEditionId(event.target.value)}
              options={(editions.data ?? []).map(item => ({ value: String(item.id), label: `${item.subject.code} · ${item.subject.name}` }))} />
            <Select label={t('directory.class')} value={section} onChange={event => current.setClassSection(event.target.value)}
              options={chosen.classSections.map(code => ({ value: code, label: code }))} />
          </div>
          <Segmented<EnrolmentMode> label={t('enrolments.how')} value={mode} onValueChange={setMode}
            options={[{ value: 'choose', label: t('enrolments.choose') }, { value: 'csv', label: t('registration.csvImport') }]} />
          {mode === 'choose' ? (
            <EnrolmentPicker key={`${chosen.id}-${section}`} onEnrol={enrol} />
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
    </CurrentEditionPage>
  );
}
