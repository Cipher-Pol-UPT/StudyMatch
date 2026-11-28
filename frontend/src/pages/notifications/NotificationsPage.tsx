import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { Button, Card, NotificationList, Segmented, type NotificationItem } from '../../studymatch-ui';
import { itemOf, pathOf } from './notificationText';
import { useNotifications } from './notificationCenter';
import './notifications.css';

type Show = 'all' | 'unread';

// every notification, newest first, 20 at a time (#145)
export function NotificationsPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { items, error, unread, role, hasMore, loadingMore, loadMore, retry, markRead, markAllRead } = useNotifications();
  const [show, setShow] = useState<Show>('all');
  // the unread ones of the pages already loaded; "Show older" brings more
  const shown = (items ?? []).filter(notification => show === 'all' || !notification.read);

  const open = (item: NotificationItem) => {
    const notification = items?.find(candidate => String(candidate.id) === item.id);
    if (!notification) return;
    markRead(notification);
    const path = pathOf(notification, role);
    if (path) navigate(path);
  };

  return (
    <div className="role-page notifications-page">
      <header>
        <h1 className="sm-h2">{t('notifications.title')}</h1>
        <p className="sm-caption">{t('notifications.intro')}</p>
      </header>
      {items === null ? (
        <DataLoadState error={error} retry={retry} />
      ) : (
        <Card className="notifications-page__card">
          <NotificationList items={shown.map(notification => itemOf(notification, t, i18n.language))} count={unread} showCount={false}
            title={<Segmented<Show> label={t('notifications.show')} size="sm" value={show} onValueChange={setShow} options={[
              { value: 'all', label: t('notifications.all') },
              { value: 'unread', label: unread ? t('notifications.unreadCount', { count: unread }) : t('notifications.unread') },
            ]} />}
            emptyText={show === 'unread' && items.length ? t('notifications.noUnread') : t('notifications.empty')}
            onItemClick={open} onMarkAllRead={markAllRead}
            footer={hasMore && <Button variant="ghost" size="sm" loading={loadingMore} onClick={loadMore}>{t('notifications.older')}</Button>} />
        </Card>
      )}
    </div>
  );
}
