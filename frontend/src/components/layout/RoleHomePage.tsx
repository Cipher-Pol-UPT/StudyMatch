import { CalendarDays } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { EmptyState } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import type { NavigationSection } from '../../types/navigation';

interface RoleHomePageProps {
  currentUser: CurrentUser;
  sections: NavigationSection[];
}

export function RoleHomePage({ currentUser, sections }: RoleHomePageProps) {
  const { t } = useTranslation();
  return (
    <div className="role-page">
      <header>
        <p className="sm-eyebrow">{t(`workspace.roles.${currentUser.role}`)}</p>
        <h1 className="sm-h2">{t('home.greeting', { name: currentUser.name })}</h1>
        <p className="sm-caption">{t('workspace.welcome')}</p>
      </header>
      <div className="role-grid">
        {sections.map(section => (
          <Link className="role-card" key={section.path} to={section.path}>
            <section.icon size={25} aria-hidden />
            <h2>{t(`workspace.sections.${section.key}`)}</h2>
            <p>{t(section.description ?? `workspace.descriptions.${section.key}`)}</p>
            <span>{t('workspace.open')} →</span>
          </Link>
        ))}
      </div>
      <EmptyState compact icon={CalendarDays} title={t('workspace.noActivity')} body={t('workspace.noActivityBody')} />
    </div>
  );
}
