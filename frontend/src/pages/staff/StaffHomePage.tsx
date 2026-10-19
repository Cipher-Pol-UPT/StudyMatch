import { CalendarDays } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { EmptyState } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import { STAFF_SECTIONS } from './sections';

export function StaffHomePage({ currentUser }: { currentUser: CurrentUser }) {
  const { t } = useTranslation();
  return (
    <div className="role-page">
      <header>
        <p className="sm-eyebrow">{t('workspace.roles.STAFF')}</p>
        <h1 className="sm-h2">{t('home.greeting', { name: currentUser.name })}</h1>
        <p className="sm-caption">{t('workspace.welcome')}</p>
      </header>
      <div className="role-grid">
        {STAFF_SECTIONS.map(section => (
          <Link className="role-card" key={section.path} to={section.path}>
            <section.icon size={25} aria-hidden />
            <h2>{t(`workspace.sections.${section.key}`)}</h2>
            <p>{t(`workspace.descriptions.${section.key}`)}</p>
            <span>{t('workspace.open')} →</span>
          </Link>
        ))}
      </div>
      <EmptyState compact icon={CalendarDays} title={t('workspace.noActivity')} body={t('workspace.noActivityBody')} />
    </div>
  );
}
