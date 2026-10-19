import { BookOpen, ClipboardList, FilePenLine, Star, Users } from 'lucide-react';
import type { NavigationSection } from '../../types/navigation';

export const TEACHER_SECTIONS: NavigationSection[] = [
  { key: 'students', path: '/students', icon: Users, ready: true, description: 'directory.chooseSubject' },
  { key: 'mySubjects', path: '/subjects', icon: BookOpen },
  { key: 'assignments', path: '/assignments', icon: ClipboardList },
  { key: 'groups', path: '/groups', icon: Users },
  { key: 'grades', path: '/grades', icon: FilePenLine },
  { key: 'tutorApplications', path: '/tutor-applications', icon: Star },
];
