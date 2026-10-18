import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { EmptyState, type IconComponent } from '../../studymatch-ui';
import './PlaceholderPage.css';

export interface PlaceholderPageProps {
  icon: IconComponent;
  title: string;
  action?: ReactNode;
}

export function PlaceholderPage({ icon, title, action }: PlaceholderPageProps) {
  const { t } = useTranslation();

  return (
    <main className="placeholder-page">
      <EmptyState icon={icon} title={title} body={t('placeholder.comingSoon')} action={action} />
    </main>
  );
}
