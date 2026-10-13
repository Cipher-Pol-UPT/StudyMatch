import { Clock, ExternalLink, Video } from 'lucide-react';
import type { MouseEvent, ReactNode } from 'react';
import { Avatar } from '../../components/avatar/Avatar';
import { StatusPill } from '../../components/badge/Badge';
import { Button, buttonClass, type ButtonSize } from '../../components/button/Button';
import { Card } from '../../components/card/Card';
import { ProgressBar } from '../../components/progress/Progress';
import { Rating } from '../../components/rating/Rating';
import { cx } from '../../utils/cx';
import { formatCountdown, studyRoomAccess, toDate, useNow, type StudyRoom, type StudyRoomAccess } from './model';
import './studyroom.css';

// ─── Meet button ───────────────────────────────────────────
export interface MeetButtonProps {
  href: string;
  /** "Enter StudyRoom" for participants, "Start StudyRoom" for the tutor. */
  children?: ReactNode;
  size?: ButtonSize;
  block?: boolean;
  /** Called on click (e.g. log the entry). The link still opens. */
  onEnter?: () => void;
  className?: string;
}

/**
 * Opens the StudyRoom’s Google Meet in a new tab. It is a real link (not window.open),
 * so pop-up blockers never stop it and middle-click / copy link work too.
 */
export function MeetButton({ href, children = 'Enter StudyRoom', size = 'md', block, onEnter, className }: MeetButtonProps) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className={cx(buttonClass({ variant: 'primary', size, block }), 'sm-meet-btn', className)}
      onClick={(e: MouseEvent<HTMLAnchorElement>) => {
        e.stopPropagation();
        onEnter?.();
      }}
    >
      <Video size={size === 'sm' ? 16 : 18} strokeWidth={2.25} aria-hidden />
      <span className="sm-btn__label">{children}</span>
      <ExternalLink size={size === 'sm' ? 13 : 15} strokeWidth={2.25} aria-hidden className="sm-meet-btn__ext" />
      <span className="sm-visually-hidden">(opens Google Meet in a new tab)</span>
    </a>
  );
}

// ─── Status ────────────────────────────────────────────────
export function StudyRoomStatusPill({ room, access, now }: { room: StudyRoom; access: StudyRoomAccess; now: Date }) {
  if (room.status === 'cancelled') return <StatusPill tone="danger" size="sm">Cancelled</StatusPill>;
  if (room.status === 'completed') return <StatusPill size="sm">Completed</StatusPill>;
  if (access.phase === 'live') return <StatusPill tone="success" size="sm" pulse>Live now</StatusPill>;
  if (access.phase === 'ended') return <StatusPill size="sm">Ended</StatusPill>;
  if (access.phase === 'soon') return <StatusPill tone="success" size="sm">Starts {formatCountdown(toDate(room.start), now)}</StatusPill>;
  if (access.isHost) return <StatusPill tone="violet" size="sm">Hosting</StatusPill>;
  if (access.joined) return <StatusPill tone="violet" size="sm">Joined</StatusPill>;
  if (access.seatsLeft === 0) return <StatusPill tone="warning" size="sm">Full</StatusPill>;
  return <StatusPill tone="info" size="sm">Scheduled</StatusPill>;
}

// ─── Card ──────────────────────────────────────────────────
export interface StudyRoomCardProps {
  room: StudyRoom;
  /** Current user id: decides Join / Leave / Enter / Start. */
  meId?: string;
  /** Rooms the user joined, to block overlapping joins. */
  joinedRooms?: StudyRoom[];
  onJoin?: (room: StudyRoom) => void;
  onLeave?: (room: StudyRoom) => void;
  /** Opens the details (the whole card is clickable when set). */
  onOpen?: (room: StudyRoom) => void;
  onEnter?: (room: StudyRoom) => void;
  /** Replace the action area. */
  action?: ReactNode;
  /** Freeze time (docs, tests). */
  now?: Date;
  locale?: string;
  className?: string;
}

/** A StudyRoom at a glance. The main action changes with time: Join → Opens at 18:20 → Enter StudyRoom. */
export function StudyRoomCard({ room, meId, joinedRooms, onJoin, onLeave, onOpen, onEnter, action, now: fixedNow, locale = 'en-GB', className }: StudyRoomCardProps) {
  const now = useNow(15_000, fixedNow);
  const access = studyRoomAccess(room, meId, now, joinedRooms);
  const start = toDate(room.start);
  const end = toDate(room.end);
  const time = new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit' });
  const month = new Intl.DateTimeFormat(locale, { month: 'short' }).format(start);
  const day = new Intl.DateTimeFormat(locale, { weekday: 'short', day: 'numeric', month: 'short' }).format(start);
  const taken = room.participants.length;
  const inactive = room.status === 'cancelled' || room.status === 'completed' || access.phase === 'ended';

  const defaultAction = (() => {
    if (inactive) return null;
    if (access.canEnter) return <MeetButton href={room.meetUrl} size="sm" onEnter={() => onEnter?.(room)}>{access.isHost ? 'Start StudyRoom' : 'Enter StudyRoom'}</MeetButton>;
    if (access.isHost) return <span className="sm-room__opens">Meet opens {time.format(access.opensAt)}</span>;
    if (access.joined)
      return (
        <span className="sm-room__opens">
          Opens {formatCountdown(access.opensAt, now)}
          {onLeave && access.canLeave && (
            <Button variant="ghost" size="sm" onClick={(e) => (e.stopPropagation(), onLeave(room))}>
              Leave
            </Button>
          )}
        </span>
      );
    if (access.canJoin)
      return (
        <Button size="sm" disabled={!!access.joinBlocked} title={access.joinBlocked} onClick={(e) => (e.stopPropagation(), onJoin?.(room))}>
          {access.seatsLeft === 0 ? 'Full' : 'Join'}
        </Button>
      );
    return null;
  })();

  return (
    <Card as="article" className={cx('sm-room', inactive && 'sm-room--inactive', room.status === 'cancelled' && 'sm-room--cancelled', access.phase === 'live' && !inactive && 'sm-room--live', className)} onClick={onOpen ? () => onOpen(room) : undefined} aria-label={`${room.subject}: ${room.topic}, ${day}`}>
      <div className="sm-room__top">
        <div className="sm-room__date" aria-hidden>
          <b>{start.getDate()}</b>
          <small>{month}</small>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="sm-room__row">
            <span className="sm-room__subject">{room.subject}</span>
            <StudyRoomStatusPill room={room} access={access} now={now} />
          </div>
          <h3 className="sm-room__topic">{room.topic}</h3>
        </div>
      </div>
      <ul className="sm-room__facts">
        <li>
          <Clock size={15} strokeWidth={2.25} aria-hidden />
          <time dateTime={start.toISOString()}>
            {day} · {time.format(start)}–{time.format(end)}
          </time>
        </li>
        <li>
          <Video size={15} strokeWidth={2.25} aria-hidden />
          Google Meet
        </li>
      </ul>
      {!inactive && (
        <div className="sm-room__seats">
          <div className="sm-room__seats-text">
            <span>Places</span>
            <span>
              {taken}/{room.capacity} · {access.seatsLeft === 0 ? 'full' : `${access.seatsLeft} left`}
            </span>
          </div>
          <ProgressBar value={taken} max={room.capacity} label="Places taken" size="sm" tone={access.seatsLeft === 0 ? 'warning' : 'brand'} />
        </div>
      )}
      <div className="sm-room__foot">
        <div className="sm-room__tutor">
          <Avatar name={room.tutor.name} src={room.tutor.avatarUrl} size={34} />
          <div style={{ minWidth: 0 }}>
            <div className="sm-room__tutor-name">{room.tutor.name}</div>
            {room.tutor.rating !== undefined && <Rating value={room.tutor.rating} readOnly size={13} showValue count={room.tutor.ratings} label="Tutor rating" />}
          </div>
        </div>
        {action ?? defaultAction}
      </div>
    </Card>
  );
}
