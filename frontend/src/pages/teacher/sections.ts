import { BookOpen, ClipboardList, FilePenLine, Star, Users } from 'lucide-react';
import type { NavigationSection } from '../../types/navigation';

export const TEACHER_SECTIONS: NavigationSection[] = [
  { key: 'mySubjects', path: '/subjects', icon: BookOpen, issues: [76] },
  { key: 'assignments', path: '/assignments', icon: ClipboardList, issues: [27] },
  { key: 'groups', path: '/groups', icon: Users, issues: [56, 58, 62, 73, 75] },
  { key: 'grades', path: '/grades', icon: FilePenLine, issues: [78] },
  { key: 'tutorApplications', path: '/tutor-applications', icon: Star, issues: [80] },
];
