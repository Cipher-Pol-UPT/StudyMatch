import { ListChecks } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button, Card, StatusPill, Table, type TableColumn } from '../../studymatch-ui';
import type { EnrolmentResultData } from '../../types/enrolments';

export interface EnrolmentResultsProps {
  results: EnrolmentResultData[];
  onDone: () => void;
}

// how many went in, and every student who didn't with the reason (and the CSV line)
export function EnrolmentResults({ results, onDone }: EnrolmentResultsProps) {
  const { t } = useTranslation();
  const skipped = results.filter(result => result.result !== 'ENROLLED');
  const fromCsv = results.some(result => result.row !== undefined);
  const columns: TableColumn<EnrolmentResultData>[] = [
    ...(fromCsv ? [{ key: 'row', header: t('registration.row'), render: (row: EnrolmentResultData) => <span className="sm-num">{row.row}</span> }] : []),
    { key: 'number', header: t('directory.number'), render: row => row.number ?? '—' },
    { key: 'name', header: t('directory.name'), render: row => row.name ?? '—' },
    { key: 'result', header: t('enrolments.reason'), render: row => <StatusPill tone="warning">{t(`enrolments.results.${row.result}`)}</StatusPill> },
  ];

  return (
    <Card className="enrolments__card">
      <h2 className="enrolments__title">{t('enrolments.done')}</h2>
      <p className="enrolments__note">{t('enrolments.summary', { enrolled: results.length - skipped.length, skipped: skipped.length })}</p>
      {skipped.length > 0 && (
        <Table label={t('enrolments.skipped')} columns={columns} rows={skipped} rowKey={(row, index) => row.row ?? `${row.number}-${index}`} />
      )}
      <Button icon={ListChecks} onClick={onDone}>{t('enrolments.enrolMore')}</Button>
    </Card>
  );
}
