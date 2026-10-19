import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { getStudentRecord } from '../../api/studentDirectory';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { useStudentFormatting } from '../../hooks/useStudentFormatting';
import { Card, ProfileView, StatusPill, Table, type TableColumn } from '../../studymatch-ui';
import type { EnrolmentRecordData } from '../../types/studentDirectory';
import { toProfileView } from '../profile/toProfileView';
import '../../styles/studentDirectory.css';

export function StaffStudentDetailsPage() {
  const { t } = useTranslation();
  const format = useStudentFormatting();
  const { number } = useParams();
  const [params] = useSearchParams();
  const id = Number(number);
  const load = useCallback(() => {
    if (!Number.isSafeInteger(id) || id <= 0) return Promise.reject({ code: 'NOT_FOUND' });
    return getStudentRecord(id);
  }, [id]);
  const record = useRemoteData(String(number), load);
  const columns: TableColumn<EnrolmentRecordData>[] = [
    { key: 'subjectName', header: t('directory.subject'), render: row => `${row.subjectCode} · ${row.subjectName}` },
    { key: 'academicYear', header: t('directory.academicYear'), render: row => `${row.academicYear}/${row.academicYear + 1}` },
    { key: 'classSection', header: t('directory.class') },
    { key: 'ects', header: 'ECTS' },
    { key: 'status', header: t('directory.status'), render: row => t(`enrolmentStatuses.${row.status}`) },
    { key: 'finalGrade', header: t('directory.finalGrade'), render: row => format.grade(row.finalGrade) },
  ];
  const data = record.data;

  return (
    <div className="role-page directory-page">
      <Link className="directory-back" to={`/students?${params}`}>← {t('directory.backToStudents')}</Link>
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
            <Card title={t('directory.specialStatute')}>
              <dl className="directory-details">
                <div>
                  <dt>{t('directory.statuteType')}</dt>
                  <dd>{data.statuteType === null ? t('directory.noStatute') : t(`statutes.${data.statuteType}`)}</dd>
                </div>
                <div><dt>{t('directory.statuteEnd')}</dt><dd>{data.statuteEndDate ?? '—'}</dd></div>
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
          <section>
            <h2 className="sm-title">{t('directory.enrolments')}</h2>
            <Table
              label={t('directory.enrolments')}
              columns={columns}
              rows={data.enrolments}
              rowKey={(_, index) => index}
              empty={<p>{t('directory.noEnrolments')}</p>}
            />
          </section>
        </>
      )}
    </div>
  );
}