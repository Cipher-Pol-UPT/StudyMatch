import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useOutletContext, useParams, useSearchParams } from 'react-router-dom';
import { getEditionStudents, getStudentProfile } from '../../api/studentDirectory';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { useStudentFormatting } from '../../hooks/useStudentFormatting';
import { Card, ProfileView } from '../../studymatch-ui';
import type { SubjectEditionData } from '../../types/studentDirectory';
import { toProfileView } from '../profile/toProfileView';
import '../../styles/studentDirectory.css';

export function TeacherStudentProfilePage() {
  const edition = useOutletContext<SubjectEditionData>();
  const { t } = useTranslation();
  const format = useStudentFormatting();
  const { number } = useParams();
  const [params] = useSearchParams();
  const id = Number(number);
  const load = useCallback(async () => {
    if (!Number.isSafeInteger(id) || id <= 0) throw { code: 'NOT_FOUND' };
    // Direct links and reloads must also check the student's enrolment.
    const query = new URLSearchParams({ search: String(id) }).toString();
    const student = (await getEditionStudents(edition.id, query)).find(row => row.number === id);
    if (!student) throw { code: 'NOT_FOUND' };
    return { student, profile: await getStudentProfile(id) };
  }, [edition.id, id]);
  const detail = useRemoteData(`edition:${edition.id}:student:${number}`, load);
  const data = detail.data;
  const profile = data && toProfileView({
    ...data.profile,
    // Show attendance for this subject, rather than the profile's overall attendance.
    privateProfile: data.profile.privateProfile && {
      ...data.profile.privateProfile,
      attendance: data.student.attendance,
    },
  });

  return (
    <>
      <Link className="directory-back" to={`/subject-editions/${edition.id}/students?${params}`}>
        ← {t('directory.backToStudents')}
      </Link>
      {detail.loading || detail.error !== undefined ? (
        <DataLoadState error={detail.error} retry={detail.retry} />
      ) : data && profile && (
        <>
          <Card title={t('directory.subjectEvidence')}>
            <dl className="directory-details">
              <div><dt>{t('directory.class')}</dt><dd>{data.student.classSection}</dd></div>
              <div><dt>{t('directory.categoryLevel')}</dt><dd>{format.grade(data.student.categoryLevel)}</dd></div>
              <div><dt>{t('directory.subjectAttendance')}</dt><dd>{format.attendance(data.student.attendance)}</dd></div>
            </dl>
          </Card>
          <ProfileView profile={profile} viewer={{ showPrivate: data.profile.privateProfile !== null, canEdit: false }} />
        </>
      )}
    </>
  );
}
