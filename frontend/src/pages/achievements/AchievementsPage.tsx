import { Trophy } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { AchievementDialog, AchievementGrid, Breadcrumbs, EmptyState, ProgressBar, Spinner, type AchievementId, type StudentProfile } from '../../studymatch-ui';
import { Achievement } from '../../types/enums';
import './achievements.css';

export interface AchievementsPageProps {
  profile: StudentProfile | null;
  failed: boolean;
}

// the earned ones first, then every other one with how to earn it
export function AchievementsPage({ profile, failed }: AchievementsPageProps) {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<AchievementId | null>(null);

  if (failed) {
    return <EmptyState icon={Trophy} title={t('common.unexpectedError')} />;
  }
  if (profile === null) {
    return <div className="achievements-page achievements-page--centered"><Spinner /></div>;
  }

  const earned = profile.achievements ?? [];
  const toEarn = Achievement.filter((id) => !earned.some((a) => a.id === id)).map((id) => ({ id }));
  const count = t('achievements.count', { earned: earned.length, total: Achievement.length });
  const opened = earned.find((a) => a.id === selected);

  return (
    <div className="achievements-page">
      <Breadcrumbs linkAs={Link} items={[{ label: t('nav.profile'), href: '/profile' }, { label: t('achievements.title') }]} />
      <header className="sm-ach-summary">
        <span className="sm-ach-summary__count">{earned.length}<small>/ {Achievement.length}</small></span>
        <div className="sm-ach-summary__bar">
          <h1>{t('achievements.title')}</h1>
          <ProgressBar value={earned.length} max={Achievement.length} label={count} showLabel />
        </div>
      </header>

      <section>
        <h2>{t('achievements.earned')}</h2>
        {earned.length > 0
          ? <AchievementGrid achievements={earned} onSelect={setSelected} />
          : <p className="achievements-page__none">{t('achievements.noneYet')}</p>}
      </section>

      {toEarn.length > 0 && (
        <section>
          <h2>{t('achievements.toEarn')}</h2>
          <p className="achievements-page__note">{t('achievements.countedRooms')}</p>
          <AchievementGrid achievements={toEarn} onSelect={setSelected} />
        </section>
      )}

      <AchievementDialog id={selected} earnedAt={opened?.earnedAt} onClose={() => setSelected(null)} />
    </div>
  );
}
