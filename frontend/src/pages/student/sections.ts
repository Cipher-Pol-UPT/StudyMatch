import { Bell, BookOpen, ClipboardList, FolderOpen, GraduationCap, User, Users } from 'lucide-react';
import type { IconComponent } from '../../studymatch-ui';

export interface Section {
  key: string;
  path: string;
  icon: IconComponent;
  color?: string;
  bg?: string;
}

export const SECTIONS: Section[] = [
  { key: 'rooms', path: '/study-rooms', icon: BookOpen, color: 'var(--sm-lead)', bg: 'var(--sm-tint-lavender)' },
  { key: 'subjects', path: '/subjects', icon: FolderOpen, color: 'var(--sm-tech)', bg: 'var(--sm-tint-sky)' },
  { key: 'groups', path: '/groups', icon: Users, color: 'var(--sm-creative)', bg: 'var(--sm-tint-peach)' },
  { key: 'assignments', path: '/assignments', icon: ClipboardList, color: 'var(--sm-exec)', bg: 'var(--sm-tint-mint)' },
];

export const ACCOUNT: Section[] = [
  { key: 'profile', path: '/profile', icon: User },
  { key: 'studentInformation', path: '/student-information', icon: GraduationCap },
  { key: 'notifications', path: '/notifications', icon: Bell },
];