import { Users } from 'lucide-react';
import { useCallback, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { getDegrees, getStudents } from '../../api/studentDirectory';
import { Button, EmptyState, Pagination, Select, StatusPill, Table, TextField, type TableColumn } from '../../studymatch-ui';
import { AccountStatus } from '../../types/enums';
import type { StudentSummaryData } from '../../types/studentDirectory';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { PersonalitySummary } from '../../components/students/PersonalitySummary';
import { useStudentFormatting } from '../../hooks/useStudentFormatting';
import '../../styles/studentDirectory.css';

const FILTERS = ['search', 'degreeId', 'curricularYear', 'status'];

export function StaffStudentsPage() {
  const { t } = useTranslation();
  const format = useStudentFormatting();
  const [params, setParams] = useSearchParams();
  const pageValue = Number(params.get('page') ?? 0);
  const page = Number.isSafeInteger(pageValue) && pageValue >= 0 ? pageValue : 0;
  const normalized = new URLSearchParams();
  for (const key of FILTERS) {
    const value = params.get(key)?.trim();
    if (value) normalized.set(key, value);
  }
  normalized.set('page', String(page));
  const query = normalized.toString();
  const load = useCallback(() => getStudents(query), [query]);
  const students = useRemoteData(query, load);
  const degrees = useRemoteData('degrees', getDegrees);
  const filtered = FILTERS.some(key => Boolean(params.get(key)?.trim()));
  const clear = () => setParams({});

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const key of FILTERS) {
      const value = String(data.get(key) ?? '').trim();
      if (value) next.set(key, value);
    }
    setParams(next);
  }

  const columns: TableColumn<StudentSummaryData>[] = [
    { key: 'number', header: t('directory.number') },
    { key: 'name', header: t('directory.name'), render: row => <Link to={`/students/${row.number}?${query}`}>{row.name}</Link> },
    { key: 'degree', header: t('directory.degree') },
    { key: 'curricularYear', header: t('directory.year') },
    { key: 'attendance', header: t('directory.attendance'), render: row => format.attendance(row.attendance) },
    { key: 'averageGrade', header: t('directory.averageGrade'), render: row => format.grade(row.averageGrade) },
    { key: 'personalityProfile', header: t('directory.personality'), render: row => <PersonalitySummary profile={row.personalityProfile} /> },
    { key: 'accountStatus', header: t('directory.status'), render: row => <StatusPill tone={row.accountStatus === 'ACTIVE' ? 'success' : 'danger'}>{t(`accountStatuses.${row.accountStatus}`)}</StatusPill> },
  ];
  const degreeOptions = [{ value: '', label: t('directory.allDegrees') }, ...(degrees.data ?? []).map(degree => ({ value: String(degree.id), label: degree.name }))];
  if (params.get('degreeId') && !degreeOptions.some(option => option.value === params.get('degreeId'))) degreeOptions.push({ value: params.get('degreeId')!, label: t('directory.degreeId', { id: params.get('degreeId') }) });

  function changePage(nextPage: number) {
    const updated = new URLSearchParams(params);
    updated.set('page', String(nextPage - 1));
    setParams(updated);
  }

  return (
    <div className="role-page directory-page">
      <header>
        <h1 className="sm-h2">{t('workspace.sections.students')}</h1>
        <p className="sm-caption">{t('directory.staffIntro')}</p>
      </header>
      <form key={query} className="directory-filters" onSubmit={applyFilters} aria-label={t('directory.filters')}>
        <TextField name="search" type="search" label={t('directory.search')} defaultValue={params.get('search') ?? ''} />
        <Select
          name="degreeId"
          label={t('directory.degree')}
          defaultValue={params.get('degreeId') ?? ''}
          options={degreeOptions}
          disabled={degrees.loading}
        />
        <Select
          name="curricularYear"
          label={t('directory.year')}
          defaultValue={params.get('curricularYear') ?? ''}
          options={[
            { value: '', label: t('directory.allYears') },
            ...Array.from({ length: 6 }, (_, index) => ({ value: String(index + 1), label: String(index + 1) })),
          ]}
        />
        <Select
          name="status"
          label={t('directory.status')}
          defaultValue={params.get('status') ?? ''}
          options={[
            { value: '', label: t('directory.allStatuses') },
            ...AccountStatus.map(status => ({ value: status, label: t(`accountStatuses.${status}`) })),
          ]}
        />
        <div className="directory-filter-actions">
          <Button type="submit">{t('directory.apply')}</Button>
          <Button variant="secondary" onClick={clear}>{t('directory.clear')}</Button>
        </div>
      </form>
      {degrees.error !== undefined && (
        <div role="alert">
          {t('directory.filterLoadFailed')}
          <Button variant="ghost" onClick={degrees.retry}>{t('profile.tryAgain')}</Button>
        </div>
      )}
      {students.loading || students.error !== undefined ? (
        <DataLoadState error={students.error} retry={students.retry} />
      ) : students.data && (
        <>
          <Table
            className="directory-table"
            label={t('workspace.sections.students')}
            columns={columns}
            rows={students.data.content}
            rowKey={row => row.number}
            empty={
              <EmptyState
                icon={Users}
                title={t(filtered ? 'directory.noMatches' : 'directory.noStudents')}
                body={t(filtered ? 'directory.clearHint' : 'directory.noStudentsBody')}
                action={filtered && <Button variant="secondary" onClick={clear}>{t('directory.clear')}</Button>}
              />
            }
          />
          <p className="sm-caption">{t('directory.attendanceNote')}</p>
          <Pagination
            page={students.data.page + 1}
            pageCount={students.data.totalPages}
            info={t('directory.total', { count: students.data.totalElements })}
            onPageChange={changePage}
          />
        </>
      )}
    </div>
  );
}
