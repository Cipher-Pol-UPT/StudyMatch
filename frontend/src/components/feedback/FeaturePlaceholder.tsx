import { useTranslation } from 'react-i18next';
import { EmptyState } from '../../studymatch-ui';
import type { NavigationSection } from '../../types/navigation';

export function FeaturePlaceholder({ section }: { section: NavigationSection }) {
  const { t } = useTranslation();
  return (
    <div className="role-page">
      <h1 className="sm-h2">{t(`workspace.sections.${section.key}`)}</h1>
      <EmptyState
        icon={section.icon}
        title={t('placeholder.comingSoon')}
        body={t(`workspace.descriptions.${section.key}`)}
      />
    </div>
  );
}
