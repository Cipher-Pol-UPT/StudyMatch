import { useEffect, useState } from 'react';
import { getMyProfile } from '../../api/students';
import type { StudentProfile } from '../../studymatch-ui';

export function useMyProfile() {
  const [state, setState] = useState<{ profile: StudentProfile | null; failed: boolean }>({ profile: null, failed: false });

  useEffect(() => {
    let alive = true;
    getMyProfile()
      .then((profile) => alive && setState({ profile, failed: false }))
      .catch(() => alive && setState({ profile: null, failed: true }));
    return () => {
      alive = false;
    };
  }, []);

  return state;
}