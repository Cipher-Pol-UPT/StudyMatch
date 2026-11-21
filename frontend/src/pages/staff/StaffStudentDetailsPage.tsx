import { ArrowLeftRight, Plus } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { changeClass, getCurrentEditions } from '../../api/enrolments';
import { isBackendError } from '../../api/errors';
import { getStudentRecord, removeStatute, setStatute } from '../../api/studentDirectory';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { useStudentFormatting } from '../../hooks/useStudentFormatting';
import { Breadcrumbs, Button, Card, ConfirmDialog, Dialog, ProfileView, StatusPill, Table, useToast, type TableColumn } from '../../studymatch-ui';
import type { EnrolmentRecordData, StatuteRequest, StudentRecordData } from '../../types/studentDirectory';
import { toProfileView } from '../profile/toProfileView';
import { ChangeClassForm } from './ChangeClassForm';
import { StatuteForm } from './StatuteForm';
import '../../styles/studentDirectory.css';

export function StaffStudentDetailsPage() {
  const { t } = useTranslation();
  const format = useStudentFormatting();
  const { number } = useParams();
  const [params] = useSearchParams();
  const id = Number(number);
  const load = useCallback(async () => {
    if (!Number.isSafeInteger(id) || id <= 0) throw { code: 'NOT_FOUND' };
    return getStudentRecord(id);
  }, [id]);
  const record = useRemoteData(String(number), load);
  // this year's editions and their classes: only their enrolments can change class
  const editions = useRemoteData('current-editions', getCurrentEditions);
  const otherClasses = (row: EnrolmentRecordData) =>
    editions.data?.find(edition => edition.id === row.subjectEditionId)?.classSections.filter(code => code !== row.classSection) ?? [];
  const [moving, setMoving] = useState<EnrolmentRecordData>();
  const columns: TableColumn<EnrolmentRecordData>[] = [
    { key: 'subjectName', header: t('directory.subject'), render: row => `${row.subjectCode} · ${row.subjectName}` },
    { key: 'academicYear', header: t('directory.academicYear'), render: row => `${row.academicYear}/${row.academicYear + 1}` },
    { key: 'classSection', header: t('directory.class') },
    { key: 'ects', header: 'ECTS' },
    { key: 'status', header: t('directory.status'), render: row => t(`enrolmentStatuses.${row.status}`) },
    { key: 'finalGrade', header: t('directory.finalGrade'), render: row => format.grade(row.finalGrade) },
    {
      key: 'actions', header: <span className="sm-visually-hidden">{t('ui.actions')}</span>, align: 'right',
      render: row => otherClasses(row).length > 0 && (
        <Button size="sm" variant="ghost" icon={ArrowLeftRight} onClick={() => setMoving(row)}
          aria-label={t('directory.changeClassOf', { subject: row.subjectName })}>
          {t('directory.changeClass')}
        </Button>
      ),
    },
  ];
  // the card shows what the backend answered last, without loading the whole record again
  const [changed, setChanged] = useState<StudentRecordData>();
  const data = changed ?? record.data;
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const [ending, setEnding] = useState(false);

  async function saveStatute(statute: StatuteRequest) {
    setChanged(await setStatute(id, statute));
    setEditing(false);
    toast.success(t('directory.statuteSaved'));
  }

  async function moveClass(classSection: string) {
    if (!data || !moving) return;
    const updated = await changeClass(moving.subjectEditionId, id, classSection);
    setChanged({ ...data, enrolments: data.enrolments.map(row => row.subjectEditionId === updated.subjectEditionId ? updated : row) });
    setMoving(undefined);
    toast.success(t('directory.classChanged', { classSection: updated.classSection }));
  }

  // someone else may have ended it already, then the card just catches up
  async function endStatute() {
    try {
      await removeStatute(id);
      toast.success(t('directory.statuteEnded'));
    } catch (error) {
      if (!isBackendError(error) || error.code !== 'STUDENT_HAS_NO_STATUTE') throw error;
      toast.info(t('errors.STUDENT_HAS_NO_STATUTE'));
    }
    if (data) setChanged({ ...data, statuteType: null, statuteEndDate: null });
  }

  return (
    <div className="role-page directory-page">
      <Breadcrumbs
        linkAs={Link}
        items={[{ label: t('workspace.sections.students'), href: `/students?${params}` }, { label: data?.profile.name ?? number }]}
      />
      {record.loading || record.error !== undefined ? (
        <DataLoadState error={record.error} retry={record.retry} />
      ) : data && (
        <>
          <ProfileView profile={toProfileView(data.profile)} viewer={{ showPrivate: data.profile.privateProfile !== null, canEdit: false }} />
          <div className="role-grid directory-record-grid">
            <Card title={t('directory.staffRecord')}>
              <dl className="directory-details">
                <div><dt>{t('directory.personalEmail')}</dt><dd>{data.personalEmail}</dd></div>
                <div>
                  <dt>{t('directory.status')}</dt>
                  <dd>
                    <StatusPill tone={data.accountStatus === 'ACTIVE' ? 'success' : 'danger'}>
                      {t(`accountStatuses.${data.accountStatus}`)}
                    </StatusPill>
                  </dd>
                </div>
                <div><dt>{t('directory.averageGrade')}</dt><dd>{format.grade(data.averageGrade)}</dd></div>
              </dl>
            </Card>
            <Card
              title={t('directory.specialStatute')}
              action={data.statuteType === null ? (
                <Button size="sm" variant="secondary" icon={Plus} onClick={() => setEditing(true)}>{t('directory.addStatute')}</Button>
              ) : (
                <div className="directory-statute-actions">
                  <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>{t('directory.replaceStatute')}</Button>
                  <Button size="sm" variant="ghost" onClick={() => setEnding(true)}>{t('directory.endStatute')}</Button>
                </div>
              )}
            >
              <dl className="directory-details">
                <div>
                  <dt>{t('directory.statuteType')}</dt>
                  <dd>{data.statuteType === null ? t('directory.noStatute') : t(`statutes.${data.statuteType}`)}</dd>
                </div>
                <div><dt>{t('directory.statuteEnd')}</dt><dd>{format.date(data.statuteEndDate)}</dd></div>
              </dl>
            </Card>
            <Card title={t('directory.categoryLevels')}>
              {data.categoryLevels.length === 0 ? (
                <p className="sm-caption">{t('directory.noGrade')}</p>
              ) : (
                <dl className="directory-details">
                  {data.categoryLevels.map(level => (
                    <div key={level.categoryId}><dt>{level.category}</dt><dd>{format.grade(level.level)}</dd></div>
                  ))}
                </dl>
              )}
            </Card>
          </div>
          <Dialog open={editing} onClose={() => setEditing(false)} size="sm"
            title={t(data.statuteType === null ? 'directory.addStatute' : 'directory.replaceStatute')}
            description={t('directory.statuteStartsToday')}>
            <StatuteForm onSubmit={saveStatute} onCancel={() => setEditing(false)} />
          </Dialog>
          <ConfirmDialog open={ending} onClose={() => setEnding(false)} onConfirm={endStatute} tone="danger"
            title={t('directory.endStatuteTitle')} description={t('directory.endStatuteDescription')}
            confirmLabel={t('directory.endStatute')} />
          <Dialog open={moving !== undefined} onClose={() => setMoving(undefined)} size="sm"
            title={t('directory.changeClass')}
            description={moving && t('directory.changeClassDescription', { subject: moving.subjectName, classSection: moving.classSection })}>
            {moving && <ChangeClassForm classSections={otherClasses(moving)} onSubmit={moveClass} onCancel={() => setMoving(undefined)} />}
          </Dialog>
          <Card title={t('directory.enrolments')}>
            <Table
              label={t('directory.enrolments')}
              columns={columns}
              rows={data.enrolments}
              rowKey={(_, index) => index}
              empty={<p>{t('directory.noEnrolments')}</p>}
            />
          </Card>
        </>
      )}
    </div>
  );
}
