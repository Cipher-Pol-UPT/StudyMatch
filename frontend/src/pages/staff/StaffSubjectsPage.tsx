import { BookOpen, Plus } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getSubjects } from '../../api/subjects';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Button, EmptyState, Table, useToast, type TableColumn } from '../../studymatch-ui';
import type { SubjectData } from '../../types/subjects';
import { SubjectDialog } from './SubjectDialog';

const byCode = (a: SubjectData, b: SubjectData) => a.code.localeCompare(b.code);

export function StaffSubjectsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const subjects = useRemoteData('subjects', getSubjects);
  const [changed, setChanged] = useState<SubjectData[]>([]);
  const [registering, setRegistering] = useState(false);
  const rows = [...new Map([...(subjects.data ?? []), ...changed].map((row) => [row.id, row])).values()].sort(byCode);
  const retry = () => {
    setChanged([]);
    subjects.retry();
  };

  const columns: TableColumn<SubjectData>[] = [
    { key: 'code', header: t('subjects.code') },
    { key: 'name', header: t('fields.name') },
    { key: 'ects', header: t('subjects.ects') },
    { key: 'category', header: t('subjects.category'), render: (row) => row.category.name },
    { key: 'degrees', header: t('subjects.degrees'), render: (row) => row.degrees.map((degree) => degree.degreeName).join(', ') },
  ];

  const registered = (subject: SubjectData) => {
    setChanged((list) => [...list, subject]);
    setRegistering(false);
    toast.success(t('subjects.subjectRegistered', { name: subject.name }));
  };

  return (
    <div className="role-page">
      <header>
        <h1 className="sm-h2">{t('workspace.sections.subjects')}</h1>
        <p className="sm-caption">{t('subjects.intro')}</p>
        <Button icon={Plus} className="subjects-page__create" onClick={() => setRegistering(true)}>{t('subjects.newSubject')}</Button>
      </header>
      {subjects.loading || subjects.error !== undefined ? (
        <DataLoadState error={subjects.error} retry={retry} />
      ) : (
        <Table
          label={t('workspace.sections.subjects')}
          columns={columns}
          rows={rows}
          rowKey={(row) => row.id}
          empty={<EmptyState icon={BookOpen} title={t('subjects.noSubjects')} body={t('subjects.noSubjectsBody')} />}
        />
      )}
      {registering && <SubjectDialog open onClose={() => setRegistering(false)} onRegistered={registered} />}
    </div>
  );
}
