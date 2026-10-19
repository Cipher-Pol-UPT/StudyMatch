import { Users } from 'lucide-react';
import { useCallback, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useOutletContext, useSearchParams } from 'react-router-dom';
import { getEditionStudents } from '../../api/studentDirectory';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { PersonalitySummary } from '../../components/students/PersonalitySummary';
import { useRemoteData } from '../../hooks/useRemoteData';
import { useStudentFormatting } from '../../hooks/useStudentFormatting';
import { Button, EmptyState, Select, Table, TextField, type TableColumn } from '../../studymatch-ui';
import type { EditionStudentData, SubjectEditionData } from '../../types/studentDirectory';
import '../../styles/studentDirectory.css';

export function TeacherStudentsPage() {
  const edition = useOutletContext<SubjectEditionData>();
  const { t } = useTranslation();
  const format = useStudentFormatting();
  const [params, setParams] = useSearchParams();
  const normalized = new URLSearchParams();
  for (const key of ['search', 'classSection']) {
    const value = params.get(key)?.trim();
    if (value) normalized.set(key, value);
  }
  const query = normalized.toString();
  const load = useCallback(() => getEditionStudents(edition.id, query), [edition.id, query]);
  const students = useRemoteData(`edition:${edition.id}:${query}`, load);
  const clear = () => setParams({});

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const key of ['search', 'classSection']) {
      const value = String(data.get(key) ?? '').trim();
      if (value) next.set(key, value);
    }
    setParams(next);
  }

  const columns: TableColumn<EditionStudentData>[] = [
    { key: 'number', header: t('directory.number') },
    {
      key: 'name', header: t('directory.name'),
      render: row => <Link to={`/subject-editions/${edition.id}/students/${row.number}?${query}`}>{row.name}</Link>,
    },
    { key: 'classSection', header: t('directory.class') },
    { key: 'personalityProfile', header: t('directory.personality'), render: row => <PersonalitySummary profile={row.personalityProfile} /> },
    { key: 'categoryLevel', header: t('directory.categoryLevel'), render: row => format.grade(row.categoryLevel) },
    { key: 'attendance', header: t('directory.subjectAttendance'), render: row => format.attendance(row.attendance) },
  ];

  return (
    <>
      <h2 className="sm-title">{t('directory.viewStudents')}</h2>
      <form
        key={query}
        className="directory-filters directory-teacher-filters"
        onSubmit={applyFilters}
        aria-label={t('directory.filters')}
      >
        <TextField name="search" type="search" label={t('directory.search')} defaultValue={params.get('search') ?? ''} />
        <Select
          name="classSection"
          label={t('directory.class')}
          defaultValue={params.get('classSection') ?? ''}
          options={[
            { value: '', label: t('directory.allClasses') },
            ...edition.classSections.map(code => ({ value: code, label: code })),
          ]}
        />
        <div className="directory-filter-actions">
          <Button type="submit">{t('directory.apply')}</Button>
          <Button variant="secondary" onClick={clear}>{t('directory.clear')}</Button>
        </div>
      </form>
      {students.loading || students.error !== undefined ? (
        <DataLoadState error={students.error} retry={students.retry} />
      ) : students.data && (
        <>
          <Table
            className="directory-table directory-table--teacher"
            label={t('directory.viewStudents')}
            columns={columns}
            rows={students.data}
            rowKey={row => row.number}
            empty={
              <EmptyState
                icon={Users}
                title={t(query ? 'directory.noMatches' : 'directory.noEnrolled')}
                body={t(query ? 'directory.clearHint' : 'directory.noEnrolledBody')}
                action={query && <Button variant="secondary" onClick={clear}>{t('directory.clear')}</Button>}
              />
            }
          />
          <p className="sm-caption">{t('directory.attendanceNote')}</p>
        </>
      )}
    </>
  );
}
