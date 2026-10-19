import { useEffect, useState } from 'react';
import { getMyProfile } from '../../api/students';
import type { StudentProfile } from '../../studymatch-ui';
import type { StudentProfileData } from '../../types/studentProfile';
import { toProfileView } from '../profile/toProfileView';

export function useMyProfile() {
  const [state, setState] = useState<{ profile: StudentProfile | null; failed: boolean }>({ profile: null, failed: false });

  useEffect(() => {
    let alive = true;
    getMyProfile()
      .then((data) => alive && setState({ profile: toProfileView(data), failed: false }))
      .catch(() => alive && setState({ profile: null, failed: true }));
    return () => {
      alive = false;
    };
  }, []);

  function showSavedProfile(saved: StudentProfileData) {
    setState({ profile: toProfileView(saved), failed: false });
  }

  return { ...state, showSavedProfile };
}
