import { RefreshCw, UserRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getMyProfile } from '../../api/students';
import { Button, EmptyState, ProfileView, Spinner, useToast } from '../../studymatch-ui';
import type { StudentProfileData } from '../../types/studentProfile';
import { EditProfile } from './EditProfile';
import { toProfileView } from './toProfileView';
import './ProfilePage.css';

export function ProfilePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const [profile, setProfile] = useState<StudentProfileData | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [isEditing, setIsEditing] = useState(false);

  useEffect(() => {
    let isCurrentAttempt = true;
    getMyProfile()
      .then(loaded => isCurrentAttempt && setProfile(loaded))
      .catch(() => isCurrentAttempt && setLoadFailed(true));
    return () => {
      isCurrentAttempt = false;
    };
  }, [loadAttempt]);

  function tryLoadingAgain() {
    setLoadFailed(false);
    setLoadAttempt(attempt => attempt + 1);
  }

  function showSavedProfile(saved: StudentProfileData) {
    setProfile(saved);
    setIsEditing(false);
    toast.success(t('profile.saved'));
  }

  if (loadFailed) {
    return (
      <main className="profile-page profile-page--centered">
        <EmptyState
          icon={UserRound}
          title={t('common.unexpectedError')}
          action={<Button variant="secondary" icon={RefreshCw} onClick={tryLoadingAgain}>{t('profile.tryAgain')}</Button>}
        />
      </main>
    );
  }

  if (profile === null) {
    return (
      <main className="profile-page profile-page--centered">
        <Spinner />
      </main>
    );
  }

  return (
    <main className="profile-page">
      {isEditing ? (
        <EditProfile profile={profile} onSaved={showSavedProfile} onCancel={() => setIsEditing(false)} />
      ) : (
        <ProfileView
          profile={toProfileView(profile)}
          viewer={{ showPrivate: true, canEdit: true }}
          onEdit={() => setIsEditing(true)}
        />
      )}
    </main>
  );
}
