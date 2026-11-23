import { ArrowLeft, MessageCircle } from 'lucide-react';
import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import { getStudentProfile } from '../../api/studentDirectory';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Button, buttonClass, ProfileView } from '../../studymatch-ui';
import { toProfileView } from '../profile/toProfileView';
import '../../styles/roleLayout.css';

export interface ClassmateProfilePageProps {
  // the logged-in student, whose own number opens My profile instead
  myNumber: number;
  profilePath: string;
}

// another student's profile, only its public part, with a way to message them (#286).
// Your own number goes to My profile before anything is loaded
export function ClassmateProfilePage({ myNumber, profilePath }: ClassmateProfilePageProps) {
  const id = Number(useParams().number);
  if (id === myNumber) return <Navigate to={profilePath} replace />;
  return <ClassmateProfile id={id} />;
}

function ClassmateProfile({ id }: { id: number }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const load = useCallback(
    () => Number.isSafeInteger(id) && id > 0 ? getStudentProfile(id) : Promise.reject(Object.assign(new Error('Not a student number'), { code: 'NOT_FOUND' })),
    [id],
  );
  const profile = useRemoteData(`student-profile:${id}`, load);

  // back to where they came from, or home when the page was opened from a link
  const back = () => (location.key === 'default' ? navigate('/') : navigate(-1));

  return (
    <div className="role-page">
      <div>
        <Button variant="ghost" size="sm" icon={ArrowLeft} onClick={back}>{t('classmate.back')}</Button>
      </div>
      {profile.loading || profile.error !== undefined || !profile.data ? (
        <DataLoadState error={profile.error} retry={profile.retry} />
      ) : (
        <ProfileView profile={toProfileView(profile.data)} viewer={{ showPrivate: false, canEdit: false }}
          actions={(
            <Link to={`/chats/direct/${id}`} className={buttonClass({ variant: 'primary' })}>
              <MessageCircle size={18} aria-hidden />{t('classmate.message')}
            </Link>
          )} />
      )}
    </div>
  );
}
