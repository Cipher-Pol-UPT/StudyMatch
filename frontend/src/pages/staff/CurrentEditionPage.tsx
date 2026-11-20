import { BookOpen } from 'lucide-react';
import { type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { EmptyState } from '../../studymatch-ui';
import type { SubjectEditionData } from '../../types/studentDirectory';
import type { CurrentEdition } from './useCurrentEdition';

export interface CurrentEditionPageProps {
  title: string;
  intro: string;
  emptyBody: string;
  current: CurrentEdition;
  children: (edition: SubjectEditionData) => ReactNode;
}

// the Staff pages that work on one edition of this year: header, then the editions loading, none, or the page
export function CurrentEditionPage({ title, intro, emptyBody, current, children }: CurrentEditionPageProps) {
  const { t } = useTranslation();
  const { editions, edition } = current;
  return (
    <div className="role-page directory-page">
      <header>
        <h1 className="sm-h2">{title}</h1>
        <p className="sm-caption">{intro}</p>
      </header>
      {editions.loading || editions.error !== undefined ? (
        <DataLoadState error={editions.error} retry={editions.retry} />
      ) : !edition ? (
        <EmptyState icon={BookOpen} title={t('enrolments.noEditions')} body={emptyBody} />
      ) : children(edition)}
    </div>
  );
}
