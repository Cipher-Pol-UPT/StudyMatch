import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { NotificationBell, type NotificationItem } from '../../studymatch-ui';
import { itemOf, pathOf } from './notificationText';
import { useNotifications } from './notificationCenter';

// the newest few in the top bar
const IN_BELL = 6;

// the bell in the top bar with what you haven't read; picking one marks it read and opens its page (#145)
export function NotificationsBell() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { items, unread, role, markRead, markAllRead } = useNotifications();
  const newest = (items ?? []).slice(0, IN_BELL);

  const open = (item: NotificationItem) => {
    const notification = newest.find(candidate => String(candidate.id) === item.id);
    if (!notification) return;
    markRead(notification);
    const path = pathOf(notification, role);
    if (path) navigate(path);
  };

  return (
    <NotificationBell items={newest.map(notification => itemOf(notification, t, i18n.language))} count={unread}
      emptyText={t('notifications.empty')} onItemClick={open} onMarkAllRead={markAllRead}
      footer={<Link to="/notifications">{t('notifications.seeAll')}</Link>} />
  );
}
