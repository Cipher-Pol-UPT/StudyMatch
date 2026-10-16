import { useTranslation } from 'react-i18next';
import { EmptyState, type IconComponent } from '../../studymatch-ui';
import './PlaceholderPage.css';

export interface PlaceholderPageProps {
  icon: IconComponent;
  title: string;
}

export function PlaceholderPage({ icon, title }: PlaceholderPageProps) {
  const { t } = useTranslation();

  return (
    <main className="placeholder-page">
      <EmptyState icon={icon} title={title} body={t('placeholder.comingSoon')} />
    </main>
  );
}
