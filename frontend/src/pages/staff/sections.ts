import { BarChart3, BookOpen, CalendarDays, ClipboardList, GraduationCap, Layers, ListChecks, ShieldCheck, UserPlus, Users } from 'lucide-react';
import type { NavigationSection } from '../../types/navigation';

export const STAFF_SECTIONS: NavigationSection[] = [
  { key: 'students', path: '/students', icon: Users, issues: [31, 36] },
  { key: 'registerStudent', path: '/students/register', icon: UserPlus, issues: [10] },
  { key: 'enrolments', path: '/enrolments', icon: ListChecks, issues: [14] },
  { key: 'studentClasses', path: '/student-classes', icon: Layers, issues: [114] },
  { key: 'statutes', path: '/student-statutes', icon: ShieldCheck, issues: [65] },
  { key: 'attendance', path: '/attendance', icon: ClipboardList, issues: [39] },
  { key: 'subjects', path: '/subjects', icon: BookOpen, issues: [20, 26] },
  { key: 'degrees', path: '/degrees', icon: GraduationCap, issues: [106, 108] },
  { key: 'categories', path: '/categories', icon: Layers, issues: [110, 112] },
  { key: 'academicYear', path: '/academic-year', icon: CalendarDays, issues: [67] },
  { key: 'analytics', path: '/analytics', icon: BarChart3, issues: [69] },
];
