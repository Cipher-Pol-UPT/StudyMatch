import type { TFunction } from 'i18next';
import { Award, CalendarClock, CalendarX, CircleCheck, ClipboardCheck, LogOut, MessageSquareReply, Star, UserCheck, UserRoundCheck, UserRoundX, Users, UsersRound } from 'lucide-react';
import type { IconComponent, NotificationItem } from '../../studymatch-ui';
import type { NotificationType, Role } from '../../types/enums';
import type { NotificationData } from '../../types/notifications';

const LOOK: Record<NotificationType, { icon: IconComponent; tone: NonNullable<NotificationItem['tone']> }> = {
  ACCOUNT_UNBLOCKED: { icon: UserCheck, tone: 'success' },
  TUTOR_APPLICATION_SUBMITTED: { icon: ClipboardCheck, tone: 'violet' },
  TUTOR_APPLICATION_APPROVED: { icon: UserRoundCheck, tone: 'success' },
  TUTOR_APPLICATION_REJECTED: { icon: UserRoundX, tone: 'danger' },
  STUDYROOM_LEFT: { icon: LogOut, tone: 'warning' },
  STUDYROOM_UPDATED: { icon: CalendarClock, tone: 'info' },
  STUDYROOM_CANCELLED: { icon: CalendarX, tone: 'danger' },
  STUDYROOM_RATING_OPEN: { icon: Star, tone: 'warning' },
  ACHIEVEMENT_EARNED: { icon: Award, tone: 'violet' },
  GROUP_PUBLISHED: { icon: Users, tone: 'success' },
  GROUP_UPDATED: { icon: UsersRound, tone: 'info' },
  TICKET_ANSWERED: { icon: MessageSquareReply, tone: 'info' },
  TICKET_RESOLVED: { icon: CircleCheck, tone: 'success' },
};

// the params the backend sends as dates, shown in the user's language
const DATES = new Set(['startsAt', 'endsAt']);

// the title and the line under it, in the user's language, from the type and its params
export function messageOf(notification: NotificationData, t: TFunction, language: string) {
  const when = new Intl.DateTimeFormat(language, { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
  const time = new Intl.DateTimeFormat(language, { hour: 'numeric', minute: '2-digit' });
  const params: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(notification.params ?? {})) {
    if (DATES.has(key) && typeof value === 'string' && !Number.isNaN(Date.parse(value))) {
      params[key] = (key === 'endsAt' ? time : when).format(new Date(value));
    } else params[key] = value;
  }
  if (typeof params.achievement === 'string') params.achievement = t(`achievements.${params.achievement}.name`);
  const key = `notifications.types.${notification.type}`;
  const reason = typeof params.reason === 'string' && params.reason ? ` ${t('notifications.reason', { reason: params.reason })}` : '';
  return { title: t(`${key}.title`, params), body: t(`${key}.body`, params) + reason };
}

// "5 min ago", "3 h ago", "2 days ago", then the date
export function timeOf(createdAt: string, language: string, now = new Date()) {
  const date = new Date(createdAt);
  const minutes = Math.round((now.getTime() - date.getTime()) / 60000);
  const relative = new Intl.RelativeTimeFormat(language, { numeric: 'auto', style: 'short' });
  if (minutes < 60) return relative.format(-Math.max(minutes, 0), 'minute');
  if (minutes < 24 * 60) return relative.format(-Math.round(minutes / 60), 'hour');
  if (minutes < 7 * 24 * 60) return relative.format(-Math.round(minutes / (24 * 60)), 'day');
  return new Intl.DateTimeFormat(language, { day: 'numeric', month: 'short', year: 'numeric' }).format(date);
}

export function itemOf(notification: NotificationData, t: TFunction, language: string): NotificationItem {
  const { title, body } = messageOf(notification, t, language);
  const look = LOOK[notification.type] ?? { icon: Award, tone: 'violet' };
  return { id: String(notification.id), title, body, time: timeOf(notification.createdAt, language), read: notification.read, ...look };
}

// the page a notification opens for this role, or null when it opens nothing. A group's notification opens Study
// groups, where the student's groups go; a teacher's tutor application opens their applications
export function pathOf(notification: NotificationData, role: Role): string | null {
  switch (notification.target) {
    case 'STUDYROOM': return '/study-rooms';
    case 'ASSIGNMENT': return '/groups';
    case 'TUTOR_APPLICATION': return role === 'TEACHER' ? '/tutor-applications' : '/study-rooms';
    case 'ACHIEVEMENT': return '/achievements';
    default: return null;
  }
}
