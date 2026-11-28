import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { getNotifications, getUnreadNotifications, markAllNotificationsRead, markNotificationRead } from '../../api/notifications';
import type { Role } from '../../types/enums';
import type { NotificationData } from '../../types/notifications';
import type { PageData } from '../../types/studentDirectory';
import { NOTIFICATIONS_REFRESH_MS, NotificationsContext, type NotificationCenter } from './notificationCenter';

const newestFirst = (a: NotificationData, b: NotificationData) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id;
// an answer that isn't a page counts as an empty one, so a bad answer never breaks the layout around it
const contentOf = (page: PageData<NotificationData> | undefined) => Array.isArray(page?.content) ? page.content : [];
// the ones asked again replace the old copies, the older pages already loaded stay
const merge = (current: NotificationData[] | null, fresh: NotificationData[]) =>
  [...new Map([...(current ?? []), ...fresh].map(item => [item.id, item])).values()].sort(newestFirst);

// the bell and the page share one list, so reading one in either place shows in both (#145)
export function NotificationsProvider({ role, children }: { role: Role; children: ReactNode }) {
  const [items, setItems] = useState<NotificationData[] | null>(null);
  const [error, setError] = useState<unknown>();
  const [unread, setUnread] = useState(0);
  const [pages, setPages] = useState({ loaded: 0, total: 0 });
  const [loadingMore, setLoadingMore] = useState(false);
  const alive = useRef(true);

  // the newest page and the count; a failure keeps what's on screen, the list only shows it before anything loaded
  const refresh = useCallback(() => {
    getNotifications(0).then(page => {
      if (!alive.current) return;
      setError(undefined);
      setItems(current => merge(current, contentOf(page)));
      setPages(current => ({ loaded: Math.max(current.loaded, 1), total: page?.totalPages ?? 0 }));
    }, failure => { if (alive.current) setError(failure); });
    getUnreadNotifications().then(count => { if (alive.current && typeof count === 'number') setUnread(count); }, () => undefined);
  }, []);

  useEffect(() => {
    alive.current = true;
    refresh();
    const timer = setInterval(refresh, NOTIFICATIONS_REFRESH_MS);
    window.addEventListener('focus', refresh);
    return () => {
      alive.current = false;
      clearInterval(timer);
      window.removeEventListener('focus', refresh);
    };
  }, [refresh]);

  const loadMore = useCallback(() => {
    setLoadingMore(true);
    getNotifications(pages.loaded).then(page => {
      setItems(current => merge(current, contentOf(page)));
      setPages({ loaded: pages.loaded + 1, total: page?.totalPages ?? 0 });
    }, () => undefined).finally(() => setLoadingMore(false));
  }, [pages.loaded]);

  // shown as read straight away; if the backend didn't take it, the next refresh shows it unread again
  const markRead = useCallback((notification: NotificationData) => {
    if (notification.read) return;
    setItems(current => current?.map(item => item.id === notification.id ? { ...item, read: true } : item) ?? null);
    setUnread(count => Math.max(count - 1, 0));
    markNotificationRead(notification.id).catch(refresh);
  }, [refresh]);

  const markAllRead = useCallback(() => {
    setItems(current => current?.map(item => ({ ...item, read: true })) ?? null);
    setUnread(0);
    markAllNotificationsRead().catch(refresh);
  }, [refresh]);

  const retry = useCallback(() => {
    setError(undefined);
    refresh();
  }, [refresh]);

  const value = useMemo<NotificationCenter>(() => ({
    role, items, error, unread, hasMore: pages.loaded < pages.total, loadingMore, loadMore, retry, markRead, markAllRead,
  }), [role, items, error, unread, pages, loadingMore, loadMore, retry, markRead, markAllRead]);

  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>;
}
