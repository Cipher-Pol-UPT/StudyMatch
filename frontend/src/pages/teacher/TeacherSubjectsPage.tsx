import { BookOpen } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { getMySubjectEditions } from '../../api/studentDirectory';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { EmptyState } from '../../studymatch-ui';
import type { SubjectTab } from './subjectTabs';
import '../../styles/studentDirectory.css';

// Students, Assignments and Grades all start here, each card opens that tab of the subject
export function TeacherSubjectsPage({ tab }: { tab: SubjectTab }) {
  const { t } = useTranslation();
  const editions = useRemoteData('my-subject-editions', getMySubjectEditions);

  if (editions.loading || editions.error !== undefined) {
    return <DataLoadState error={editions.error} retry={editions.retry} />;
  }

  return (
    <div className="role-page directory-page">
      <header>
        <h1 className="sm-h2">{t(`workspace.sections.${tab}`)}</h1>
        <p className="sm-caption">{t(`subjectTabs.choose.${tab}`)}</p>
      </header>
      {editions.data?.length === 0 ? (
        <EmptyState icon={BookOpen} title={t('directory.noSubjects')} body={t('directory.noSubjectsBody')} />
      ) : (
        <div className="role-grid">
          {editions.data?.map(edition => (
            <Link className="role-card" key={edition.id} to={`/subject-editions/${edition.id}/${tab}`}>
              <BookOpen size={25} aria-hidden />
              <h2>{edition.subject.name}</h2>
              <p>
                {edition.subject.code} · {edition.academicYear}/{edition.academicYear + 1} · {edition.subject.category.name}
              </p>
              <span>{t(`subjectTabs.open.${tab}`)} →</span>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
