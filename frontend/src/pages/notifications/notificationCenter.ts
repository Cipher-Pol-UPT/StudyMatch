import { createContext, useContext } from 'react';
import type { Role } from '../../types/enums';
import type { NotificationData } from '../../types/notifications';

// how often the bell asks for new ones while the app is open
export const NOTIFICATIONS_REFRESH_MS = 60_000;

export interface NotificationCenter {
  role: Role;
  // null until the first page answers
  items: NotificationData[] | null;
  error: unknown;
  unread: number;
  hasMore: boolean;
  loadingMore: boolean;
  loadMore: () => void;
  retry: () => void;
  markRead: (notification: NotificationData) => void;
  markAllRead: () => void;
}

export const NotificationsContext = createContext<NotificationCenter | null>(null);

export function useNotifications() {
  const center = useContext(NotificationsContext);
  if (!center) throw new Error('NotificationsProvider is required.');
  return center;
}
