import { LayoutDashboard } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Card, EmptyState, IconTile } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import '../home/home.css';
import type { WorkspaceSection } from './workspaceSections';

export interface WorkspaceHomeProps {
  currentUser: CurrentUser;
  sections: WorkspaceSection[];
}

export function WorkspaceHome({ currentUser, sections }: WorkspaceHomeProps) {
  const { t } = useTranslation();

  return (
    <div className="home">
      <header className="home__head">
        <h1>{t('home.greeting', { name: currentUser.name })}</h1>
        <p>{t('workspace.signedInAs', { role: t(`roles.${currentUser.role}`), email: currentUser.institutionalEmail })}</p>
      </header>

      {sections.length ? (
        <section aria-labelledby="workspace-actions">
          <h2 id="workspace-actions">{t('home.quickActions')}</h2>
          <div className="home__actions">
            {sections.map(section => (
              <Link key={section.key} to={section.path} className="home-action">
                <Card>
                  <IconTile icon={section.icon} color={section.color} bg={section.bg} size={48} shape="square" />
                  <h3>{t(`nav.${section.key}`)}</h3>
                  <p>{t(`workspace.actions.${section.key}`)}</p>
                </Card>
              </Link>
            ))}
          </div>
        </section>
      ) : (
        <EmptyState icon={LayoutDashboard} title={t('workspace.nothingYet')} body={t('placeholder.comingSoon')} />
      )}
    </div>
  );
}
