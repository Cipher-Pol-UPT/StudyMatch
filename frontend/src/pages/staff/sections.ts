import { BarChart3, BookOpen, CalendarDays, ClipboardList, GraduationCap, Layers, ListChecks, ShieldCheck, UserPlus, Users } from 'lucide-react';
import type { NavigationSection } from '../../types/navigation';

export const STAFF_SECTIONS: NavigationSection[] = [
  { key: 'students', path: '/students', icon: Users, ready: true },
  { key: 'registerStudent', path: '/students/register', icon: UserPlus },
  { key: 'enrolments', path: '/enrolments', icon: ListChecks },
  { key: 'studentClasses', path: '/student-classes', icon: Layers },
  { key: 'statutes', path: '/student-statutes', icon: ShieldCheck },
  { key: 'attendance', path: '/attendance', icon: ClipboardList },
  { key: 'subjects', path: '/subjects', icon: BookOpen },
  { key: 'degrees', path: '/degrees', icon: GraduationCap },
  { key: 'categories', path: '/categories', icon: Layers },
  { key: 'academicYear', path: '/academic-year', icon: CalendarDays },
  { key: 'analytics', path: '/analytics', icon: BarChart3 },
];
