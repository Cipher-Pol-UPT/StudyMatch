import { Bell,BellOff } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import type { IconComponent } from '../../utils/shared';
import { Badge } from '../badge/Badge';
import { IconButton } from '../button/Button';
import { Popover } from '../overlay/Popover';
import './notification.css';

export interface NotificationItem {
  id: string;
  title: ReactNode;
  body?: ReactNode;
  /** Already formatted, e.g. "2 min ago". */
  time?: ReactNode;
  read?: boolean;
  icon?: IconComponent;
  /** Icon tile colour. */
  tone?: 'violet' | 'success' | 'warning' | 'danger' | 'info';
}

export interface NotificationListProps {
  items: NotificationItem[];
  onItemClick?: (item: NotificationItem) => void;
  onMarkAllRead?: () => void;
  emptyText?: ReactNode;
  title?: ReactNode;
  className?: string;
}

const TONE: Record<NonNullable<NotificationItem['tone']>, [string, string]> = {
  violet: ['var(--sm-violet-50)', 'var(--sm-violet)'],
  success: ['var(--sm-success-50)', 'var(--sm-success)'],
  warning: ['var(--sm-warning-50)', 'var(--sm-warning-ink)'],
  danger: ['var(--sm-danger-50)', 'var(--sm-danger)'],
  info: ['var(--sm-tint-sky)', '#1f86c0'],
};

/** In-app notifications (GR8): groups published, StudyRoom changes, achievements. */
export function NotificationList({ items, onItemClick, onMarkAllRead, emptyText = i18n.t('ui.you_re_all_caught_up'), title = i18n.t('ui.notifications'), className }: NotificationListProps) {
  useTranslation();
  const unread = items.filter((i) => !i.read).length;
  return (
    <div className={cx('sm-notifs', className)}>
      <div className="sm-notifs__head">
        <span className="sm-notifs__title">
          {title}
          {unread > 0 && <Badge>{unread}</Badge>}
        </span>
        {onMarkAllRead && unread > 0 && (
          <button type="button" className="sm-notifs__link" onClick={onMarkAllRead}>
             {i18n.t('ui.mark_all_as_read')} </button>
        )}
      </div>
      {items.length === 0 ? (
        <div className="sm-notifs__empty">
          <BellOff size={22} strokeWidth={2} aria-hidden />
          {emptyText}
        </div>
      ) : (
        <ul className="sm-notifs__list">
          {items.map((n) => {
            const Icon = n.icon ?? Bell;
            const [bg, fg] = TONE[n.tone ?? 'violet'];
            const content = (
              <>
                <span className="sm-notifs__icon" style={{ background: bg, color: fg }} aria-hidden>
                  <Icon size={18} strokeWidth={2.25} />
                </span>
                <span className="sm-notifs__text">
                  <span className="sm-notifs__item-title">{n.title}</span>
                  {n.body && <span className="sm-notifs__body">{n.body}</span>}
                  {n.time && <span className="sm-notifs__time">{n.time}</span>}
                </span>
                {!n.read && <span className="sm-notifs__dot" aria-label={i18n.t('ui.unread')} />}
              </>
            );
            return (
              <li key={n.id} data-read={!!n.read}>
                {onItemClick ? (
                  <button type="button" className="sm-notifs__item" onClick={() => onItemClick(n)}>
                    {content}
                  </button>
                ) : (
                  <div className="sm-notifs__item">{content}</div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

export interface NotificationBellProps extends NotificationListProps {
  align?: 'start' | 'end';
}

/** Bell icon with an unread badge that opens the notification list. */
export function NotificationBell({ align = 'end', ...list }: NotificationBellProps) {
  useTranslation();
  const unread = list.items.filter((i) => !i.read).length;
  return (
    <span className="sm-bell">
      <Popover
        align={align}
        label={i18n.t('ui.notifications')}
        width={380}
        className="sm-notifs-pop"
        trigger={<IconButton icon={Bell} label={unread ? i18n.t('ui.unread_notifications', {count:unread}) : i18n.t('ui.notifications')} variant="secondary" />}
      >
        <NotificationList {...list} />
      </Popover>
      {unread > 0 && (
        <Badge tone="danger" className="sm-bell__badge" aria-hidden>
          {unread > 9 ? '9+' : unread}
        </Badge>
      )}
    </span>
  );
}
