import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Card, IconTile, StudentPass, type StudentProfile } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import { SECTIONS } from '../student/sections';
import { GettingStartedCard, NextAchievementCard, TutoringCard } from './HomeCards';
import './home.css';
import { TaskCalendar } from './TaskCalendar';
import type { CalendarTask } from './types';

export interface HomePageProps {
  currentUser: CurrentUser;
  profile: StudentProfile | null;
  failed?: boolean;
  tasks?: CalendarTask[];
  onTaskClick?: (task: CalendarTask) => void;
}

export function HomePage({ currentUser, profile, failed, tasks = [], onTaskClick }: HomePageProps) {
  const { t } = useTranslation();

  return (
    <div className="home">
      <header className="home__head">
        <div>
          <h1>{t('home.greeting', { name: currentUser.name })}</h1>
          {profile && !profile.dominantProfile && (
            <p>{t('home.studentInfo', { programme: profile.programme, year: profile.curricularYear, number: profile.studentNumber })}</p>
          )}
          {failed && <p role="alert">{t('common.unexpectedError')}</p>}
        </div>
        {profile?.dominantProfile && (
          <StudentPass
            name={profile.name}
            number={profile.studentNumber}
            year={profile.curricularYear}
            programme={profile.programme}
            profile={profile.dominantProfile}
          />
        )}
      </header>

      {profile && (
        <div className="home__cards">
          <GettingStartedCard profile={profile} />
          <NextAchievementCard profile={profile} />
          <TutoringCard profile={profile} />
        </div>
      )}

      <section aria-labelledby="home-actions">
        <h2 id="home-actions">{t('home.quickActions')}</h2>
        <div className="home__actions">
          {SECTIONS.filter((s) => s.ready).map((s) => (
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
          <h2 id="home-agenda">{t('home.thisWeek')}</h2>
          <TaskCalendar tasks={tasks} weeks={1} showWeekends onTaskClick={onTaskClick} />
          {tasks.length === 0 && <p className="home__hint">{t('home.noTasksBody')}</p>}
        </Card>
      </section>
    </div>
  );
}