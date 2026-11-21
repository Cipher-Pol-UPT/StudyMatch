import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Outlet, useLocation, useMatch, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { getMySubjectEditions } from '../../api/studentDirectory';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Breadcrumbs, Tabs } from '../../studymatch-ui';
import { SUBJECT_TABS, type SubjectTab } from './subjectTabs';
import '../../styles/studentDirectory.css';

export function TeacherSubjectLayout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { editionId } = useParams();
  const id = Number(editionId);
  const load = useCallback(async () => {
    if (!Number.isSafeInteger(id) || id <= 0) throw { code: 'NOT_FOUND' };
    const edition = (await getMySubjectEditions()).find(item => item.id === id);
    if (!edition) throw { code: 'NOT_FOUND' };
    return edition;
  }, [id]);
  const edition = useRemoteData(`edition:${editionId}`, load);
  // on a student's page or an assignment's groups the trail goes one step further, and the subject links back to its list
  const student = useMatch('/subject-editions/:editionId/students/:number');
  const groups = useMatch('/subject-editions/:editionId/assignments/:assignmentId/groups');
  const [params] = useSearchParams();
  const tab = SUBJECT_TABS.find(item => pathname.startsWith(`/subject-editions/${editionId}/${item}`)) ?? 'students';

  return (
    <div className="role-page directory-page">
      <Breadcrumbs
        linkAs={Link}
        items={[
          { label: t(`workspace.sections.${tab}`), href: `/${tab}` },
          {
            label: edition.data?.subject.name ?? t('directory.subject'),
            href: student ? `/subject-editions/${editionId}/students?${params}` : groups ? `/subject-editions/${editionId}/assignments` : undefined,
          },
          ...(student ? [{ label: student.params.number }] : []),
          ...(groups ? [{ label: t('assignments.groups') }] : []),
        ]}
      />
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
          <Tabs<SubjectTab> label={t('subjectTabs.label')} value={tab}
            onValueChange={next => navigate(`/subject-editions/${editionId}/${next}`)}
            tabs={SUBJECT_TABS.map(value => ({ value, label: t(`workspace.sections.${value}`) }))} />
          <Outlet context={edition.data} />
        </>
      )}
    </div>
  );
}
