import { Lock } from 'lucide-react';
import { useMemo, useState, type FormEvent } from 'react';
import { Button } from '../../components/button/Button';
import { Card } from '../../components/card/Card';
import { TagInput } from '../../components/chip/TagInput';
import { Textarea } from '../../components/field/Textarea';
import { Banner } from '../../components/overlay/Banner';
import { ConfirmDialog } from '../../components/overlay/Dialog';
import { useToast } from '../../components/overlay/Toast';
import type { CroppedImage } from './ImageCropper';
import { AvatarUploader, BannerUploader, type BannerPreset } from './ProfileMedia';
import type { StudentProfile } from './ProfileView';
import './profile.css';

export interface ProfileEdits {
  /** undefined = unchanged · null = removed · object = new photo */
  avatar?: CroppedImage | null;
  banner?: CroppedImage | null;
  bannerPreset?: BannerPreset;
  aboutMe: string;
  skills: string[];
  interests: string[];
  languages: string[];
}

export interface EditProfileFormProps {
  profile: StudentProfile;
  /** Persist the edits (upload images, PATCH the profile). Throw to show an error. */
  onSave: (edits: ProfileEdits) => Promise<void> | void;
  onCancel?: () => void;
  skillSuggestions?: string[];
  interestSuggestions?: string[];
  languageSuggestions?: string[];
  maxAboutMe?: number;
}

const same = (a: string[], b: string[]) => a.length === b.length && a.every((x, i) => x === b[i]);

/**
 * Edit Profile use case: photo, cover, about me (≤ 500), skills, interests and languages (≥ 1).
 * Name, student number and emails are managed by Staff and shown read-only.
 * Cancel with unsaved changes asks before discarding.
 */
export function EditProfileForm({
  profile,
  onSave,
  onCancel,
  skillSuggestions = ['TypeScript', 'React', 'Python', 'Figma', 'SQL', 'Public speaking', 'UX research'],
  interestSuggestions = ['Games', 'AI', 'Music', 'Sports', 'Photography', 'Startups'],
  languageSuggestions = ['Portuguese', 'English', 'Spanish', 'French', 'German'],
  maxAboutMe = 500,
}: EditProfileFormProps) {
  const toast = useToast();
  const [avatar, setAvatar] = useState<CroppedImage | null | undefined>(undefined);
  const [banner, setBanner] = useState<CroppedImage | null | undefined>(undefined);
  const [preset, setPreset] = useState<BannerPreset>(profile.bannerPreset ?? 'violet');
  const [aboutMe, setAboutMe] = useState(profile.aboutMe ?? '');
  const [skills, setSkills] = useState(profile.skills);
  const [interests, setInterests] = useState(profile.interests);
  const [languages, setLanguages] = useState(profile.languages);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmDiscard, setConfirmDiscard] = useState(false);

  const avatarSrc = avatar === undefined ? profile.avatarUrl : avatar?.url ?? null;
  const bannerSrc = banner === undefined ? profile.bannerUrl : banner?.url ?? null;

  const dirty = useMemo(
    () =>
      avatar !== undefined ||
      banner !== undefined ||
      preset !== (profile.bannerPreset ?? 'violet') ||
      aboutMe !== (profile.aboutMe ?? '') ||
      !same(skills, profile.skills) ||
      !same(interests, profile.interests) ||
      !same(languages, profile.languages),
    [avatar, banner, preset, aboutMe, skills, interests, languages, profile],
  );

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (languages.length === 0) {
      setError('Add at least one language. Every group must share a language with you.');
      return;
    }
    if (aboutMe.length > maxAboutMe) {
      setError(`About me can have up to ${maxAboutMe} characters.`);
      return;
    }
    setError(null);
    setBusy(true);
    try {
      await onSave({ avatar, banner, bannerPreset: preset, aboutMe: aboutMe.trim(), skills, interests, languages });
      toast.success('Profile updated');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Couldn’t save your profile. Try again.');
    } finally {
      setBusy(false);
    }
  };

  const cancel = () => (dirty ? setConfirmDiscard(true) : onCancel?.());

  return (
    <form className="sm-edit-profile" onSubmit={submit} noValidate>
      <div className="sm-edit-profile__media">
        <BannerUploader
          src={bannerSrc}
          preset={preset}
          height={180}
          onImageChange={setBanner}
          onPresetChange={(p) => {
            setPreset(p);
            setBanner(null);
          }}
        />
        <div className="sm-edit-profile__avatar">
          <AvatarUploader name={profile.name} src={avatarSrc} onChange={setAvatar} size={112} />
          <span className="sm-caption">Photo and cover: JPG or PNG, up to 5 MB.</span>
        </div>
      </div>

      <Card title="Managed by academic services" action={<Lock size={16} color="var(--sm-text-3)" aria-hidden />} tone="gray">
        <dl className="sm-edit-profile__locked">
          <div>
            <dt>Name</dt>
            <dd>{profile.name}</dd>
          </div>
          <div>
            <dt>Student number</dt>
            <dd className="sm-num">{profile.studentNumber}</dd>
          </div>
          <div>
            <dt>Institutional email</dt>
            <dd>{profile.institutionalEmail}</dd>
          </div>
        </dl>
      </Card>

      <Card title="About you">
        <div className="sm-edit-profile__fields">
          <Textarea label="About me" maxLength={maxAboutMe} value={aboutMe} onChange={(e) => setAboutMe(e.target.value)} placeholder="What do you enjoy working on? What are you learning?" optional />
          <TagInput label="Skills" value={skills} onValueChange={setSkills} max={15} suggestions={skillSuggestions} />
          <TagInput label="Interests" value={interests} onValueChange={setInterests} max={15} suggestions={interestSuggestions} />
          <TagInput
            label="Languages"
            value={languages}
            onValueChange={(l) => {
              setLanguages(l);
              if (l.length) setError(null);
            }}
            suggestions={languageSuggestions}
            hint="At least one. Changes only affect groups generated from now on."
          />
          <p className="sm-caption" style={{ margin: 0 }}>
            About me, skills and interests are shown on your profile only; they’re never used to form groups.
          </p>
        </div>
      </Card>

      {error && <Banner tone="danger">{error}</Banner>}

      <div className="sm-edit-profile__bar">
        <span className="sm-caption">{dirty ? 'You have unsaved changes.' : 'No changes yet.'}</span>
        <div style={{ display: 'flex', gap: 8 }}>
          {onCancel && (
            <Button variant="secondary" onClick={cancel} disabled={busy}>
              Cancel
            </Button>
          )}
          <Button type="submit" loading={busy} disabled={!dirty}>
            Save profile
          </Button>
        </div>
      </div>

      <ConfirmDialog
        open={confirmDiscard}
        onClose={() => setConfirmDiscard(false)}
        onConfirm={() => onCancel?.()}
        tone="danger"
        title="Discard your changes?"
        description="Your profile stays as it was."
        confirmLabel="Discard"
        cancelLabel="Keep editing"
      />
    </form>
  );
}
