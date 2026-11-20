import { ClipboardList, Users } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useOutletContext } from 'react-router-dom';
import { isBackendError } from '../../api/errors';
import { getGradeSheet, gradeStudent } from '../../api/grades';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, ConfirmDialog, EmptyState, StatusPill, Table, useToast, type StatusTone, type TableColumn } from '../../studymatch-ui';
import type { EnrolmentStatus } from '../../types/enums';
import type { GradeColumn, GradeRow } from '../../types/grades';
import type { SubjectEditionData } from '../../types/studentDirectory';
import { GradeCell } from './GradeCell';
import './grades.css';

const STATUS_TONE: Record<EnrolmentStatus, StatusTone> = { IN_PROGRESS: 'neutral', PASSED: 'success', FAILED: 'danger' };

interface PendingChange { row: GradeRow; column: GradeColumn; from: number; to: number; answer: (confirmed: boolean) => void }

// one row per student, one column per assignment; changing a grade that's already there asks first
export function TeacherGradesPage() {
  const edition = useOutletContext<SubjectEditionData>();
  const { t } = useTranslation();
  const toast = useToast();
  const load = useCallback(() => getGradeSheet(edition.id), [edition.id]);
  const sheet = useRemoteData(`grades:${edition.id}`, load);
  // what saving gave back, on top of the sheet that was loaded
  const [saved, setSaved] = useState<Record<number, GradeRow>>({});
  const [weightsComplete, setWeightsComplete] = useState<boolean>();
  const [pending, setPending] = useState<PendingChange | null>(null);
  const total = sheet.data?.assignments.reduce((sum, assignment) => sum + assignment.weight, 0) ?? 0;

  async function commit(row: GradeRow, column: GradeColumn, value: number) {
    const from = row.grades[column.id];
    if (from !== undefined) {
      const confirmed = await new Promise<boolean>(answer => setPending({ row, column, from, to: value, answer }));
      if (!confirmed) return null;
    }
    try {
      const result = await gradeStudent(edition.id, column.id, row.number, value);
      setSaved(current => ({ ...current, [row.number]: result.student }));
      setWeightsComplete(result.weightsComplete);
      return undefined;
    } catch (error) {
      if (isBackendError(error) && error.fieldErrors?.length) return t('grades.range');
      toast.danger(isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError'));
      return null;
    }
  }

  function answer(confirmed: boolean) {
    pending?.answer(confirmed);
    setPending(null);
  }

  if (sheet.loading || sheet.error !== undefined || !sheet.data) {
    return <DataLoadState error={sheet.error} retry={sheet.retry} />;
  }

  const { assignments } = sheet.data;
  // the warning already says the total, so the caption doesn't repeat it
  const incomplete = assignments.length > 0 && !(weightsComplete ?? sheet.data.weightsComplete);
  const rows = sheet.data.students.map(row => saved[row.number] ?? row);
  const columns: TableColumn<GradeRow>[] = [
    {
      key: 'name', header: t('attendance.student'),
      render: row => <span className="grades__student">{row.name} <span className="sm-caption">· {row.number}</span></span>,
    },
    ...assignments.map((column): TableColumn<GradeRow> => ({
      key: `assignment-${column.id}`, header: `${column.name} · ${column.weight}%`, align: 'right',
      render: row => <GradeCell value={row.grades[column.id]} label={t('grades.cellLabel', { name: row.name, assignment: column.name })}
        onCommit={value => commit(row, column, value)} />,
    })),
    { key: 'finalGrade', header: t('grades.final'), align: 'right', render: row => <span className="sm-num">{row.finalGrade ?? '—'}</span> },
    { key: 'status', header: t('directory.status'), render: row => <StatusPill tone={STATUS_TONE[row.status]}>{t(`enrolmentStatuses.${row.status}`)}</StatusPill> },
  ];

  return (
    <>
      <div>
        <h2 className="sm-title">{t('workspace.sections.grades')}</h2>
        {!incomplete && <p className="sm-caption">{t('assignments.weightsTotal', { total })}</p>}
      </div>
      {incomplete && (
        <Banner tone="warning" title={t('grades.incompleteTitle', { total })}>{t('grades.incompleteBody')}</Banner>
      )}
      {assignments.length === 0 ? (
        <EmptyState icon={ClipboardList} title={t('grades.noAssignments')} body={t('grades.noAssignmentsBody')} />
      ) : (
        <Table label={t('workspace.sections.grades')} columns={columns} rows={rows} rowKey={row => row.number}
          empty={<EmptyState icon={Users} title={t('grades.noStudents')} body={t('grades.noStudentsBody')} />} />
      )}
      <ConfirmDialog open={pending !== null} onClose={() => answer(false)} onConfirm={() => answer(true)}
        title={pending ? t('grades.changeTitle', { name: pending.row.name }) : ''}
        description={pending ? t('grades.changeBody', { assignment: pending.column.name, from: pending.from, to: pending.to }) : ''}
        confirmLabel={t('grades.change')} />
    </>
  );
}
