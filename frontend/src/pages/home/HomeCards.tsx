import { CheckCircle2, ChevronRight, Circle, Plus, Star } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { AchievementTile, buttonClass, Card, Rating, ScoreRing, type StudentProfile } from '../../studymatch-ui';
import { Achievement } from '../../types/enums';

interface HomeCardProps {
  profile: StudentProfile;
}

// the first achievement still locked, with what it takes to earn it
export function NextAchievementCard({ profile }: HomeCardProps) {
  const { t } = useTranslation();
  const next = Achievement.find((id) => !profile.achievements?.some((a) => a.id === id && a.earnedAt));
  if (!next) return null;

  return (
    <Card title={t('home.nextAchievement')} className="home-card">
      <div className="home-next">
        <AchievementTile id={next} artSize={64} showRarity={false} locked />
        <div>
          <b>{t(`achievements.${next}.name`)}</b>
          <p>{t(`achievements.${next}.criteria`)}</p>
        </div>
      </div>
      <Link className={buttonClass({ variant: 'ghost', size: 'sm' })} to="/achievements">
        <span className="sm-btn__label">{t('home.seeAchievements')}</span>
        <ChevronRight size={16} strokeWidth={2.25} aria-hidden />
      </Link>
    </Card>
  );
}

// the optional parts of the profile, gone once they're all filled in
export function GettingStartedCard({ profile }: HomeCardProps) {
  const { t } = useTranslation();
  const steps = [
    { key: 'photo', done: !!profile.avatarUrl },
    { key: 'aboutMe', done: !!profile.aboutMe },
    { key: 'skills', done: profile.skills.length > 0 },
    { key: 'interests', done: profile.interests.length > 0 },
  ];
  const done = steps.filter((s) => s.done).length;
  if (done === steps.length) return null;
  const progress = t('home.gettingStarted.done', { done, total: steps.length });

  return (
    <Card title={t('home.gettingStarted.title')} description={t('home.gettingStarted.body')} className="home-card">
      <div className="home-start">
        <ScoreRing value={Math.round((done / steps.length) * 100)} size={64} label={progress}>
          <span className="home-start__count">{done}/{steps.length}</span>
        </ScoreRing>
        <ul>
          {steps.map((s) => (
            <li key={s.key} data-done={s.done}>
              {s.done ? <CheckCircle2 size={16} aria-hidden /> : <Circle size={16} aria-hidden />}
              {t(`home.gettingStarted.${s.key}`)}
            </li>
          ))}
        </ul>
      </div>
      <Link className={buttonClass({ variant: 'secondary', size: 'sm' })} to="/profile">
        <span className="sm-btn__label">{t('home.gettingStarted.action')}</span>
      </Link>
    </Card>
  );
}

// only for tutors: the subjects they tutor with their rating
export function TutoringCard({ profile }: HomeCardProps) {
  const { t } = useTranslation();
  if (!profile.tutorBadges?.length) return null;

  return (
    <Card title={t('home.tutoring.title')} className="home-card">
      <ul className="home-tutoring">
        {profile.tutorBadges.map((badge) => (
          <li key={badge.subject}>
            <span className="home-tutoring__icon" aria-hidden><Star size={16} strokeWidth={2.25} /></span>
            <span>
              <b>{badge.subject}</b>
              {badge.rating !== undefined && <Rating value={badge.rating} readOnly size={13} showValue count={badge.ratings} label={t('ui.tutor_rating')} />}
            </span>
          </li>
        ))}
      </ul>
      <Link className={buttonClass({ variant: 'secondary', size: 'sm' })} to="/study-rooms">
        <Plus size={16} strokeWidth={2.25} aria-hidden />
        <span className="sm-btn__label">{t('home.tutoring.newRoom')}</span>
      </Link>
    </Card>
  );
}
