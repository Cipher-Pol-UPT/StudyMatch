import { ClipboardList, Plus, Users } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useOutletContext } from 'react-router-dom';
import { createAssignment } from '../../api/assignments';
import { getGradeSheet } from '../../api/grades';
import { getEditionStudents } from '../../api/studentDirectory';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Button, buttonClass, Dialog, EmptyState, Table, useToast, type TableColumn } from '../../studymatch-ui';
import type { AssignmentRequest } from '../../types/assignments';
import type { GradeColumn } from '../../types/grades';
import type { SubjectEditionData } from '../../types/studentDirectory';
import { AssignmentForm } from './AssignmentForm';
import './assignments.css';

// the assignments come from the grade sheet, it's the one call that lists them with their weights
export function TeacherAssignmentsPage() {
  const edition = useOutletContext<SubjectEditionData>();
  const { t } = useTranslation();
  const toast = useToast();
  const [creating, setCreating] = useState(false);
  const loadSheet = useCallback(() => getGradeSheet(edition.id), [edition.id]);
  const sheet = useRemoteData(`grades:${edition.id}`, loadSheet);
  const loadStudents = useCallback(() => getEditionStudents(edition.id), [edition.id]);
  const students = useRemoteData(`edition:${edition.id}:pairs`, loadStudents);
  const total = sheet.data?.assignments.reduce((sum, assignment) => sum + assignment.weight, 0) ?? 0;

  async function create(assignment: AssignmentRequest) {
    const created = await createAssignment(edition.id, assignment);
    setCreating(false);
    toast.success(t('assignments.created', { name: created.name }));
    sheet.retry();
  }

  const columns: TableColumn<GradeColumn>[] = [
    { key: 'name', header: t('assignments.name') },
    { key: 'type', header: t('assignments.type'), render: row => t(`assignments.types.${row.type}`) },
    { key: 'weight', header: t('assignments.weightColumn'), align: 'right', render: row => <span className="sm-num">{row.weight}%</span> },
    {
      key: 'actions', header: <span className="sm-visually-hidden">{t('ui.actions')}</span>, align: 'right',
      render: row => row.groupWork && (
        <Link className={buttonClass({ variant: 'secondary', size: 'sm' })} to={`${row.id}/groups`} aria-label={t('groups.of', { name: row.name })}>
          <Users size={14} aria-hidden="true" />{t('assignments.groups')}
        </Link>
      ),
    },
  ];

  return (
    <>
      <div className="assignments__head">
        <div>
          <h2 className="sm-title">{t('workspace.sections.assignments')}</h2>
          {sheet.data && <p className="sm-caption">{t('assignments.weightsTotal', { total })}</p>}
        </div>
        <Button icon={Plus} disabled={!sheet.data} onClick={() => setCreating(true)}>{t('assignments.new')}</Button>
      </div>
      {sheet.loading || sheet.error !== undefined ? (
        <DataLoadState error={sheet.error} retry={sheet.retry} />
      ) : sheet.data && (
        <Table label={t('workspace.sections.assignments')} columns={columns} rows={sheet.data.assignments} rowKey={row => row.id}
          empty={<EmptyState icon={ClipboardList} title={t('assignments.none')} body={t('assignments.noneBody')} />} />
      )}
      <Dialog open={creating} onClose={() => setCreating(false)} size="lg" title={t('assignments.new')}
        description={`${edition.subject.name} · ${edition.academicYear}/${edition.academicYear + 1}`}>
        {creating && (
          <AssignmentForm remaining={100 - total} students={students.data ?? []} onSubmit={create} onCancel={() => setCreating(false)} />
        )}
      </Dialog>
    </>
  );
}
