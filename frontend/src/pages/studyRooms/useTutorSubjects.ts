import { useEffect, useState } from 'react';
import { getMyTutorSubjects } from '../../api/studyRooms';
import type { TutorSubjectData } from '../../types/studyRooms';

// empty for students who don't tutor anything (or if it can't load), which hides "New StudyRoom"
export function useTutorSubjects() {
  const [subjects, setSubjects] = useState<TutorSubjectData[]>([]);

  useEffect(() => {
    let alive = true;
    getMyTutorSubjects()
      .then((data) => alive && setSubjects(data))
      .catch(() => alive && setSubjects([]));
    return () => {
      alive = false;
    };
  }, []);

  return subjects;
}
