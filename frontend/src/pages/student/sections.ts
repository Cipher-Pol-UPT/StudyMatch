import { Bell, LifeBuoy, BookOpen, ClipboardList, FolderOpen, GraduationCap, KeyRound, MessagesSquare, User, Users } from 'lucide-react';
import type { IconComponent } from '../../studymatch-ui';

export interface Section {
  key: string;
  path: string;
  icon: IconComponent;
  color?: string;
  bg?: string;
  // has a real page; the others open a "coming soon" placeholder
  ready?: boolean;
}

export const SECTIONS: Section[] = [
  { key: 'rooms', path: '/study-rooms', icon: BookOpen, color: 'var(--sm-lead)', bg: 'var(--sm-tint-lavender)', ready: true },
  { key: 'chats', path: '/chats', icon: MessagesSquare, color: 'var(--sm-tech)', bg: 'var(--sm-tint-sky)', ready: true },
  { key: 'help', path: '/help', icon: LifeBuoy, color: 'var(--sm-exec)', bg: 'var(--sm-tint-mint)', ready: true },
  { key: 'subjects', path: '/subjects', icon: FolderOpen, color: 'var(--sm-tech)', bg: 'var(--sm-tint-sky)' },
  { key: 'groups', path: '/groups', icon: Users, color: 'var(--sm-creative)', bg: 'var(--sm-tint-peach)' },
  { key: 'assignments', path: '/assignments', icon: ClipboardList, color: 'var(--sm-exec)', bg: 'var(--sm-tint-mint)' },
];

export const ACCOUNT: Section[] = [
  { key: 'profile', path: '/profile', icon: User },
  { key: 'studentInformation', path: '/student-information', icon: GraduationCap },
  { key: 'notifications', path: '/notifications', icon: Bell },
  { key: 'changePassword', path: '/change-password', icon: KeyRound },
];