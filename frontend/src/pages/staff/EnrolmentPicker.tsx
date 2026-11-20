import { UserPlus, Users } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getStudents } from '../../api/studentDirectory';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, Checkbox, EmptyState, Pagination, SearchField, Table, useFormFeedback, type TableColumn } from '../../studymatch-ui';
import type { StudentSummaryData } from '../../types/studentDirectory';

export interface EnrolmentPickerProps {
  onEnrol: (studentNumbers: number[]) => Promise<void>;
}

// the ticks stay when searching or changing page, so students from different searches go in together
export function EnrolmentPicker({ onEnrol }: EnrolmentPickerProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number[]>([]);
  const query = new URLSearchParams({ ...(search.trim() ? { search: search.trim() } : {}), page: String(page) }).toString();
  const load = useCallback(() => getStudents(query), [query]);
  const students = useRemoteData(query, load);

  const toggle = (number: number, checked: boolean) =>
    setSelected(numbers => checked ? [...numbers, number] : numbers.filter(value => value !== number));

  const columns: TableColumn<StudentSummaryData>[] = [
    {
      key: 'select', header: <span className="sm-visually-hidden">{t('enrolments.select')}</span>, width: 48,
      render: row => <Checkbox aria-label={row.name} checked={selected.includes(row.number)} disabled={feedback.busy}
        onCheckedChange={checked => toggle(row.number, checked)} />,
    },
    { key: 'number', header: t('directory.number') },
    { key: 'name', header: t('directory.name') },
    { key: 'degree', header: t('directory.degree') },
    { key: 'curricularYear', header: t('directory.year') },
  ];

  return (
    <div className="enrolments__picker">
      <SearchField label={t('directory.search')} value={search} onValueChange={value => { setSearch(value); setPage(0); }} />
      {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
      {students.loading || students.error !== undefined ? (
        <DataLoadState error={students.error} retry={students.retry} />
      ) : students.data && (
        <>
          <Table label={t('workspace.sections.students')} columns={columns} rows={students.data.content} rowKey={row => row.number}
            empty={<EmptyState icon={Users} title={t('directory.noMatches')} body={t('enrolments.noStudentsBody')} />} />
          <Pagination page={students.data.page + 1} pageCount={students.data.totalPages}
            info={t('directory.total', { count: students.data.totalElements })} onPageChange={next => setPage(next - 1)} />
        </>
      )}
      <div className="enrolments__actions">
        <Button icon={UserPlus} loading={feedback.busy} disabled={selected.length === 0}
          onClick={() => void feedback.submit(() => onEnrol(selected))}>
          {t('enrolments.enrolSelected', { count: selected.length })}
        </Button>
      </div>
    </div>
  );
}
