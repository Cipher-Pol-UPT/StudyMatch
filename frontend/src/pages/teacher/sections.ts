import { BookOpen, LifeBuoy, ClipboardList, FilePenLine, MessagesSquare, Star, Users } from 'lucide-react';
import type { NavigationSection } from '../../types/navigation';

export const TEACHER_SECTIONS: NavigationSection[] = [
  { key: 'students', path: '/students', icon: Users, ready: true, description: 'directory.chooseSubject' },
  { key: 'mySubjects', path: '/subjects', icon: BookOpen },
  { key: 'assignments', path: '/assignments', icon: ClipboardList, ready: true },
  { key: 'groups', path: '/groups', icon: Users },
  { key: 'grades', path: '/grades', icon: FilePenLine, ready: true },
  { key: 'chats', path: '/chats', icon: MessagesSquare, ready: true },
  { key: 'tutorApplications', path: '/tutor-applications', icon: Star },
  { key: 'help', path: '/help', icon: LifeBuoy, ready: true },
];
