import { CalendarDays } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { ACHIEVEMENT_ART, Card, EmptyState, IconTile, StatCard, type StudentProfile } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import { SECTIONS } from '../student/sections';
import { DEMO_TASKS } from './demoTasks';
import './home.css';
import { TaskCalendar } from './TaskCalendar';
import type { CalendarTask } from './types';

export interface HomePageProps {
  currentUser: CurrentUser;
  profile: StudentProfile | null;
  failed?: boolean;
  tasks?: CalendarTask[];
  average?: number;
  onTaskClick?: (task: CalendarTask) => void;
}

export function HomePage({ currentUser, profile, failed, tasks = DEMO_TASKS, average, onTaskClick }: HomePageProps) {
  const { t } = useTranslation();
  const earned = profile?.achievements?.filter((a) => a.earnedAt).length ?? 0;
  const attendance = profile?.attendance;

  return (
    <div className="home">
      <header className="home__head">
        <h1>{t('home.greeting', { name: currentUser.name })}</h1>
        {profile && (
          <p>{t('home.studentInfo', { programme: profile.programme, year: profile.curricularYear, number: profile.studentNumber })}</p>
        )}
        {failed && <p role="alert">{t('common.unexpectedError')}</p>}
      </header>

      {profile && (
        <div className="home__stats">
          {attendance !== undefined && (
            <StatCard
              label={t('ui.attendance')}
              value={attendance ?? '—'}
              unit={attendance === null ? undefined : '%'}
              caption={attendance === null ? t('ui.attendance_not_applicable') : undefined}
            />
          )}
          <StatCard label={t('home.achievements')} value={earned} unit={`/${Object.keys(ACHIEVEMENT_ART).length}`} />
          {average !== undefined && <StatCard label={t('home.average')} value={average} />}
        </div>
      )}

      <section aria-labelledby="home-actions">
        <h2 id="home-actions">{t('home.quickActions')}</h2>
        <div className="home__actions">
          {SECTIONS.map((s) => (
            <Link key={s.key} to={s.path} className="home-action">
              <Card>
                <IconTile icon={s.icon} color={s.color} bg={s.bg} size={48} shape="square" />
                <h3>{t(`nav.${s.key}`)}</h3>
                <p>{t(`home.actions.${s.key}`)}</p>
              </Card>
            </Link>
          ))}
        </div>
      </section>

      <section aria-labelledby="home-agenda">
        <Card>
          <h2 id="home-agenda">{t('home.upcomingTasks')}</h2>
          {tasks.length ? (
            <TaskCalendar tasks={tasks} onTaskClick={onTaskClick} />
          ) : (
            <EmptyState compact icon={CalendarDays} title={t('home.noTasks')} body={t('home.noTasksBody')} />
          )}
        </Card>
      </section>
    </div>
  );
}