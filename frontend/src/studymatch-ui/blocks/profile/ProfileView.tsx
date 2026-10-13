import { AtSign,GraduationCap,Languages,Lock,Pencil,Sparkles,Star } from 'lucide-react';
import { useState,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { AchievementBadge,AchievementDialog } from '../../achievements/Achievement';
import type { AchievementId,AchievementState } from '../../achievements/catalog';
import { Avatar } from '../../components/avatar/Avatar';
import { StatusPill,type ProfileKey } from '../../components/badge/Badge';
import { PROFILES } from '../../components/badge/Badge.shared';
import { Button } from '../../components/button/Button';
import { Card } from '../../components/card/Card';
import { ProfileBars } from '../../components/charts/Charts';
import { Chip,ChipGroup } from '../../components/chip/Chip';
import { EmptyState } from '../../components/empty/EmptyState';
import { Dialog } from '../../components/overlay/Dialog';
import { ScoreRing } from '../../components/progress/Progress';
import { Rating } from '../../components/rating/Rating';
import { cx } from '../../utils/cx';
import type { CroppedImage } from './ImageCropper';
import './profile.css';
import { AvatarUploader,BannerUploader,ProfileBanner,type BannerPreset } from './ProfileMedia';
import { LEVEL } from './ProfileView.shared';

export type CategoryLevel = 'high' | 'average' | 'developing';

export interface StudentProfile {
  dominantProfile?: ProfileKey;
  name: string;
  studentNumber: string;
  institutionalEmail: string;
  curricularYear: number;
  programme: string;
  avatarUrl?: string | null;
  bannerUrl?: string | null;
  bannerPreset?: BannerPreset;
  /** About text supplied by the caller. */
  aboutMe?: string;
  skills: string[];
  interests: string[];
  /** Backend language codes. */
  languages: import('../../../types/enums').Language[];
  /** Personality profile for the current academic year (private). */
  personality?: Record<ProfileKey, number>;
  /** Level per course category, from final grades (GR3, private). */
  categoryLevels?: { category: string; level: CategoryLevel; subjects?: number }[];
  /** Overall attendance % (private). Null for students with a special statute. */
  attendance?: number | null;
  tutorBadges?: { subject: string; rating?: number; ratings?: number }[];
  achievements?: AchievementState[];
}

/**
 * Who is looking. Business rule (View My Profile): other students see only the public part
 * (banner, name, about me, skills, interests, languages, badges). Grades, attendance, category
 * levels and the personality profile are visible to the student, their teachers and staff.
 */
export interface ProfileViewer { showPrivate: boolean; canEdit: boolean }

// â”€â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
export interface ProfileHeaderProps {
  profile: StudentProfile;
  viewer?: ProfileViewer;
  /** Make the photo and cover editable in place. */
  editable?: boolean;
  onAvatarChange?: (image: CroppedImage | null) => void;
  onBannerChange?: (image: CroppedImage | null) => void;
  onBannerPresetChange?: (preset: BannerPreset) => void;
  /** Right side, e.g. an "Edit profile" button. */
  actions?: ReactNode;
  className?: string;
}

export function ProfileHeader({ profile: p, viewer = { showPrivate: false, canEdit: false }, editable, onAvatarChange, onBannerChange, onBannerPresetChange, actions, className }: ProfileHeaderProps) {
  useTranslation();
  const dominant = viewer.showPrivate ? p.dominantProfile : undefined;
  return (
    <header className={cx('sm-profile-head', className)}>
      {editable && onBannerChange ? (
        <BannerUploader src={p.bannerUrl} preset={p.bannerPreset} onImageChange={onBannerChange} onPresetChange={onBannerPresetChange} />
      ) : (
        <ProfileBanner src={p.bannerUrl} preset={p.bannerPreset} />
      )}
      <div className="sm-profile-head__body">
        <div className="sm-profile-head__avatar">
          {editable && onAvatarChange ? (
            <AvatarUploader name={p.name} src={p.avatarUrl} onChange={onAvatarChange} size={128} />
          ) : (
            <span className="sm-profile-head__ring">
              <Avatar name={p.name} src={p.avatarUrl ?? undefined} size={128} profile={dominant} />
            </span>
          )}
        </div>
        <div className="sm-profile-head__text">
          <h1 className="sm-profile-head__name">{p.name}</h1>
          <div className="sm-profile-head__meta">
            <span>
              <GraduationCap size={15} aria-hidden /> {p.programme} {i18n.t('ui.curricular_year', {year:p.curricularYear})}
            </span>
            <span className="sm-num">{p.studentNumber}</span>
            {viewer.showPrivate && (
              <span>
                <AtSign size={14} aria-hidden /> {p.institutionalEmail}
              </span>
            )}
          </div>
          {((p.tutorBadges?.length ?? 0) > 0 || dominant) && (
            <div className="sm-profile-head__tags">
              {dominant && (
                <StatusPill tone="violet" size="sm">
                  {PROFILES[dominant].label}
                </StatusPill>
              )}
              {p.tutorBadges?.map((t) => (
                <StatusPill key={t.subject} tone="warning" size="sm">
                  {i18n.t('ui.tutor_subject', {subject:t.subject})}
                </StatusPill>
              ))}
            </div>
          )}
        </div>
        {actions && <div className="sm-profile-head__actions">{actions}</div>}
      </div>
    </header>
  );
}

// â”€â”€â”€ Sections â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function PrivateNote() {
  useTranslation();
  return (
    <span className="sm-profile-private" title={i18n.t('ui.only_you_your_teachers_and_staff_see_this')}>
      <Lock size={12} strokeWidth={2.5} aria-hidden />  {i18n.t('ui.private')} </span>
  );
}

function Tags({ label, items, icon }: { label: string; items: string[]; icon?: typeof Languages }) {
  useTranslation();
  return (
    <div className="sm-profile-tags">
      <span className="sm-eyebrow">{label}</span>
      {items.length ? (
        <ChipGroup>
          {items.map((t) => (
            <Chip key={t} size="sm" icon={icon}>
              {t}
            </Chip>
          ))}
        </ChipGroup>
      ) : (
        <span className="sm-caption"> {i18n.t('ui.nothing_added_yet')} </span>
      )}
    </div>
  );
}

export interface ProfileViewProps {
  profile: StudentProfile;
  viewer?: ProfileViewer;
  /** Shows "Edit profile" (viewer = self). */
  onEdit?: () => void;
  /** Edit the photo and cover directly on the profile. */
  onAvatarChange?: (image: CroppedImage | null) => void;
  onBannerChange?: (image: CroppedImage | null) => void;
  onBannerPresetChange?: (preset: BannerPreset) => void;
  onExportCertificate?: (id: AchievementId) => void;
  locale?: string;
  className?: string;
}

/**
 * View My Profile (and another student's public profile).
 * Header with cover and photo, about, skills / interests / languages, personality,
 * category levels and attendance (private), and badges: Tutor per subject and achievements.
 */
export function ProfileView({ profile: p, viewer = { showPrivate: false, canEdit: false }, onEdit, onAvatarChange, onBannerChange, onBannerPresetChange, onExportCertificate, locale = i18n.language, className }: ProfileViewProps) {
  useTranslation();
  const [achievement, setAchievement] = useState<AchievementId | null>(null);
  const [tutor, setTutor] = useState<NonNullable<StudentProfile['tutorBadges']>[number] | null>(null);
  const isSelf = viewer.canEdit;
  const showPrivate = viewer.showPrivate;
  const earned = (p.achievements ?? []).filter((a) => a.earnedAt);
  const opened = achievement ? p.achievements?.find((a) => a.id === achievement) : undefined;
  const hasHistory = (p.categoryLevels?.length ?? 0) > 0 || p.attendance != null;

  return (
    <div className={cx('sm-profile', className)}>
      <ProfileHeader
        profile={p}
        viewer={viewer}
        editable={isSelf}
        onAvatarChange={onAvatarChange}
        onBannerChange={onBannerChange}
        onBannerPresetChange={onBannerPresetChange}
        actions={
          isSelf && onEdit ? (
            <Button variant="secondary" icon={Pencil} onClick={onEdit}>
               {i18n.t('ui.edit_profile')} </Button>
          ) : undefined
        }
      />

      <div className="sm-profile__grid">
        <div className="sm-profile__col">
          <Card title={i18n.t('ui.about')}>
            {p.aboutMe ? (
              <p className="sm-profile-about">{p.aboutMe}</p>
            ) : (
              <p className="sm-caption" style={{ margin: 0 }}>
                {isSelf ? i18n.t('ui.tell_your_classmates_what_you_enjoy_working_on') : i18n.t('ui.no_description_yet')}
              </p>
            )}
            <div className="sm-profile-tags-wrap">
              <Tags label={i18n.t('ui.skills')} items={p.skills} />
              <Tags label={i18n.t('ui.interests')} items={p.interests} />
              <Tags label={i18n.t('ui.languages')} items={p.languages.map(code => i18n.t(`languages.${code}`))} icon={Languages} />
            </div>
          </Card>

          <Card title={i18n.t('ui.badges')} description={earned.length || p.tutorBadges?.length ? i18n.t('ui.select_a_badge_for_details') : undefined}>
            {p.tutorBadges && p.tutorBadges.length > 0 && (
              <div className="sm-profile-tutor">
                {p.tutorBadges.map((t) => (
                  <button key={t.subject} type="button" className="sm-profile-tutor__item" onClick={() => setTutor(t)}>
                    <span className="sm-profile-tutor__icon" aria-hidden>
                      <Star size={18} strokeWidth={2.25} />
                    </span>
                    <span style={{ minWidth: 0 }}>
                      <b>{i18n.t('ui.tutor_subject', {subject:t.subject})}</b>
                      {t.rating !== undefined && <Rating value={t.rating} readOnly size={13} showValue count={t.ratings} label={i18n.t('ui.tutor_rating')} />}
                    </span>
                  </button>
                ))}
              </div>
            )}
            {earned.length > 0 ? (
              <div className="sm-profile-badges">
                {earned.map((a) => (
                  <AchievementBadge key={a.id} id={a.id} size={68} onClick={() => setAchievement(a.id)} />
                ))}
              </div>
            ) : (
              !p.tutorBadges?.length && <EmptyState compact icon={Sparkles} title={i18n.t('ui.no_badges_yet')} body={i18n.t('ui.achievements_are_awarded_automatically_as_you_use_studymatch')} />
            )}
          </Card>
        </div>

        {showPrivate && (
          <div className="sm-profile__col">
            <Card title={i18n.t('ui.personality_profile')} action={<PrivateNote />} description={i18n.t('ui.current_academic_year')}>
              {p.personality ? <ProfileBars values={p.personality} /> : <p className="sm-caption" style={{ margin: 0 }}> {i18n.t('ui.the_personality_test_hasn_t_been_taken_this_year')} </p>}
            </Card>
            <Card title={i18n.t('ui.academic')} action={<PrivateNote />}>
              {hasHistory ? (
                <div className="sm-profile-academic">
                  {p.attendance !== undefined && (
                    <div className="sm-profile-attendance">
                      {p.attendance === null ? (
                        <span className="sm-caption"> {i18n.t('ui.attendance_not_applicable')} </span>
                      ) : (
                        <>
                          <ScoreRing value={p.attendance} size={84} stroke={8} color="var(--sm-exec)" label={i18n.t('ui.attendance')}>
                            {p.attendance}%
                          </ScoreRing>
                          <span className="sm-caption"> {i18n.t('ui.attendance')} </span>
                        </>
                      )}
                    </div>
                  )}
                  <ul className="sm-profile-levels">
                    {p.categoryLevels?.map((c) => (
                      <li key={c.category}>
                        <span>
                          {c.category}
                          {c.subjects !== undefined && <small>{i18n.t('ui.subject_count', {count:c.subjects})}</small>}
                        </span>
                        <StatusPill tone={LEVEL[c.level].tone} size="sm">
                          {LEVEL[c.level].label}
                        </StatusPill>
                      </li>
                    ))}
                  </ul>
                </div>
              ) : (
                <EmptyState compact icon={GraduationCap} title={i18n.t('ui.no_academic_history_yet')} body={i18n.t('ui.category_levels_and_attendance_appear_after_your_first_final_grad')} />
              )}
            </Card>
          </div>
        )}
      </div>

      <AchievementDialog id={achievement} earnedAt={opened?.earnedAt} progress={opened?.progress} onClose={() => setAchievement(null)} onExportCertificate={isSelf ? onExportCertificate : undefined} locale={locale} />
      <Dialog open={!!tutor} onClose={() => setTutor(null)} title={tutor ? i18n.t('ui.tutor_subject', {subject:tutor.subject}) : ''} icon={Star} iconTone="warning" size="sm">
        {tutor && (
          <div style={{ display: 'grid', gap: 10 }}>
            <p className="sm-caption" style={{ margin: 0 }}>
              {p.name.split(' ')[0]}  {i18n.t('ui.is_an_approved_tutor_in')} {tutor.subject}  {i18n.t('ui.and_hosts_studyrooms_for_other_students')} </p>
            {tutor.rating !== undefined ? <Rating value={tutor.rating} readOnly size={22} showValue count={tutor.ratings} label={i18n.t('ui.tutor_rating')} /> : <span className="sm-caption"> {i18n.t('ui.no_ratings_yet')} </span>}
          </div>
        )}
      </Dialog>
    </div>
  );
}

/** Small summary for lists and hover cards: photo, name, programme and earned badges. */
export function ProfileCard({ profile: p, onClick }: { profile: StudentProfile; onClick?: () => void }) {
  useTranslation();
  const earned = (p.achievements ?? []).filter((a) => a.earnedAt).slice(0, 4);
  return (
    <Card padding="none" onClick={onClick} className="sm-profile-card">
      <ProfileBanner src={p.bannerUrl} preset={p.bannerPreset} height={84} />
      <div className="sm-profile-card__body">
        <span className="sm-profile-head__ring sm-profile-card__avatar">
          <Avatar name={p.name} src={p.avatarUrl ?? undefined} size={64} />
        </span>
        <b>{p.name}</b>
        <span className="sm-caption">
          {p.programme} {i18n.t('ui.curricular_year', {year:p.curricularYear})}
        </span>
        {earned.length > 0 && (
          <span className="sm-profile-card__badges">
            {earned.map((a) => (
              <AchievementBadge key={a.id} id={a.id} size={30} />
            ))}
          </span>
        )}
      </div>
    </Card>
  );
}
