import { AtSign, GraduationCap, Languages, Lock, Pencil, Sparkles, Star } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { AchievementBadge, AchievementDialog } from '../../achievements/Achievement';
import type { AchievementId, AchievementState } from '../../achievements/catalog';
import { Avatar } from '../../components/avatar/Avatar';
import { PROFILES, StatusPill, type ProfileKey } from '../../components/badge/Badge';
import { Button } from '../../components/button/Button';
import { Card } from '../../components/card/Card';
import { ProfileBars } from '../../components/charts/Charts';
import { Chip, ChipGroup } from '../../components/chip/Chip';
import { EmptyState } from '../../components/empty/EmptyState';
import { Dialog } from '../../components/overlay/Dialog';
import { ScoreRing } from '../../components/progress/Progress';
import { Rating } from '../../components/rating/Rating';
import { cx } from '../../utils/cx';
import { AvatarUploader, BannerUploader, ProfileBanner, type BannerPreset } from './ProfileMedia';
import type { CroppedImage } from './ImageCropper';
import './profile.css';

export type CategoryLevel = 'high' | 'average' | 'developing';

export interface StudentProfile {
  name: string;
  studentNumber: string;
  institutionalEmail: string;
  curricularYear: number;
  programme: string;
  avatarUrl?: string | null;
  bannerUrl?: string | null;
  bannerPreset?: BannerPreset;
  /** Up to 500 characters. */
  aboutMe?: string;
  skills: string[];
  interests: string[];
  /** At least one (GR4). */
  languages: string[];
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
export type ProfileViewer = 'self' | 'public' | 'staff';

export const LEVEL: Record<CategoryLevel, { label: string; tone: 'success' | 'info' | 'violet' }> = {
  high: { label: 'High', tone: 'success' },
  average: { label: 'Average', tone: 'info' },
  developing: { label: 'Developing', tone: 'violet' },
};

// ─── Header ────────────────────────────────────────────────
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

export function ProfileHeader({ profile: p, viewer = 'self', editable, onAvatarChange, onBannerChange, onBannerPresetChange, actions, className }: ProfileHeaderProps) {
  const dominant = p.personality && viewer !== 'public' ? (Object.keys(p.personality) as ProfileKey[]).sort((a, b) => p.personality![b] - p.personality![a])[0] : undefined;
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
              <GraduationCap size={15} aria-hidden /> {p.programme} · Year {p.curricularYear}
            </span>
            <span className="sm-num">{p.studentNumber}</span>
            {viewer !== 'public' && (
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
                  Tutor · {t.subject}
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

// ─── Sections ──────────────────────────────────────────────
function PrivateNote() {
  return (
    <span className="sm-profile-private" title="Only you, your teachers and staff see this">
      <Lock size={12} strokeWidth={2.5} aria-hidden /> Private
    </span>
  );
}

function Tags({ label, items, icon }: { label: string; items: string[]; icon?: typeof Languages }) {
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
        <span className="sm-caption">Nothing added yet.</span>
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
export function ProfileView({ profile: p, viewer = 'self', onEdit, onAvatarChange, onBannerChange, onBannerPresetChange, onExportCertificate, locale = 'en-GB', className }: ProfileViewProps) {
  const [achievement, setAchievement] = useState<AchievementId | null>(null);
  const [tutor, setTutor] = useState<NonNullable<StudentProfile['tutorBadges']>[number] | null>(null);
  const isSelf = viewer === 'self';
  const showPrivate = viewer !== 'public';
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
              Edit profile
            </Button>
          ) : undefined
        }
      />

      <div className="sm-profile__grid">
        <div className="sm-profile__col">
          <Card title="About">
            {p.aboutMe ? (
              <p className="sm-profile-about">{p.aboutMe}</p>
            ) : (
              <p className="sm-caption" style={{ margin: 0 }}>
                {isSelf ? 'Tell your classmates what you enjoy working on.' : 'No description yet.'}
              </p>
            )}
            <div className="sm-profile-tags-wrap">
              <Tags label="Skills" items={p.skills} />
              <Tags label="Interests" items={p.interests} />
              <Tags label="Languages" items={p.languages} icon={Languages} />
            </div>
          </Card>

          <Card title="Badges" description={earned.length || p.tutorBadges?.length ? 'Select a badge for details.' : undefined}>
            {p.tutorBadges && p.tutorBadges.length > 0 && (
              <div className="sm-profile-tutor">
                {p.tutorBadges.map((t) => (
                  <button key={t.subject} type="button" className="sm-profile-tutor__item" onClick={() => setTutor(t)}>
                    <span className="sm-profile-tutor__icon" aria-hidden>
                      <Star size={18} strokeWidth={2.25} />
                    </span>
                    <span style={{ minWidth: 0 }}>
                      <b>Tutor · {t.subject}</b>
                      {t.rating !== undefined && <Rating value={t.rating} readOnly size={13} showValue count={t.ratings} label="Tutor rating" />}
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
              !p.tutorBadges?.length && <EmptyState compact icon={Sparkles} title="No badges yet" body="Achievements are awarded automatically as you use StudyMatch." />
            )}
          </Card>
        </div>

        {showPrivate && (
          <div className="sm-profile__col">
            <Card title="Personality profile" action={<PrivateNote />} description="Current academic year">
              {p.personality ? <ProfileBars values={p.personality} /> : <p className="sm-caption" style={{ margin: 0 }}>The personality test hasn’t been taken this year.</p>}
            </Card>
            <Card title="Academic" action={<PrivateNote />}>
              {hasHistory ? (
                <div className="sm-profile-academic">
                  {p.attendance !== undefined && (
                    <div className="sm-profile-attendance">
                      {p.attendance === null ? (
                        <span className="sm-caption">Attendance not applicable</span>
                      ) : (
                        <>
                          <ScoreRing value={p.attendance} size={84} stroke={8} color="var(--sm-exec)" label="Attendance">
                            {p.attendance}%
                          </ScoreRing>
                          <span className="sm-caption">Attendance</span>
                        </>
                      )}
                    </div>
                  )}
                  <ul className="sm-profile-levels">
                    {p.categoryLevels?.map((c) => (
                      <li key={c.category}>
                        <span>
                          {c.category}
                          {c.subjects !== undefined && <small>{c.subjects} subjects</small>}
                        </span>
                        <StatusPill tone={LEVEL[c.level].tone} size="sm">
                          {LEVEL[c.level].label}
                        </StatusPill>
                      </li>
                    ))}
                  </ul>
                </div>
              ) : (
                <EmptyState compact icon={GraduationCap} title="No academic history yet" body="Category levels and attendance appear after your first final grades." />
              )}
            </Card>
          </div>
        )}
      </div>

      <AchievementDialog id={achievement} earnedAt={opened?.earnedAt} progress={opened?.progress} onClose={() => setAchievement(null)} onExportCertificate={isSelf ? onExportCertificate : undefined} locale={locale} />
      <Dialog open={!!tutor} onClose={() => setTutor(null)} title={tutor ? `Tutor · ${tutor.subject}` : ''} icon={Star} iconTone="warning" size="sm">
        {tutor && (
          <div style={{ display: 'grid', gap: 10 }}>
            <p className="sm-caption" style={{ margin: 0 }}>
              {p.name.split(' ')[0]} is an approved tutor in {tutor.subject} and hosts StudyRooms for other students.
            </p>
            {tutor.rating !== undefined ? <Rating value={tutor.rating} readOnly size={22} showValue count={tutor.ratings} label="Tutor rating" /> : <span className="sm-caption">No ratings yet.</span>}
          </div>
        )}
      </Dialog>
    </div>
  );
}

/** Small summary for lists and hover cards: photo, name, programme and earned badges. */
export function ProfileCard({ profile: p, onClick }: { profile: StudentProfile; onClick?: () => void }) {
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
          {p.programme} · Year {p.curricularYear}
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
