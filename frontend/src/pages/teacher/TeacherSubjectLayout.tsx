import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Outlet, useParams } from 'react-router-dom';
import { getMySubjectEditions } from '../../api/studentDirectory';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import '../../styles/studentDirectory.css';

export function TeacherSubjectLayout() {
  const { t } = useTranslation();
  const { editionId } = useParams();
  const id = Number(editionId);
  const load = useCallback(async () => {
    if (!Number.isSafeInteger(id) || id <= 0) throw { code: 'NOT_FOUND' };
    const edition = (await getMySubjectEditions()).find(item => item.id === id);
    if (!edition) throw { code: 'NOT_FOUND' };
    return edition;
  }, [id]);
  const edition = useRemoteData(`edition:${editionId}`, load);

  return (
    <div className="role-page directory-page">
      <Link className="directory-back" to="/students">← {t('workspace.sections.students')}</Link>
      {edition.loading || edition.error !== undefined ? (
        <DataLoadState error={edition.error} retry={edition.retry} />
      ) : edition.data && (
        <>
          <header>
            <h1 className="sm-h2">{edition.data.subject.name}</h1>
            <p className="directory-subject-meta">
              <span>{edition.data.subject.code}</span>
              <span>{edition.data.academicYear}/{edition.data.academicYear + 1}</span>
              <span>{edition.data.subject.category.name}</span>
            </p>
          </header>
          <Outlet context={edition.data} />
        </>
      )}
    </div>
  );
}
