import { Clock3, Users } from 'lucide-react';
import { useId, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Avatar, Button, Card, ProgressBar, Rating } from '../../studymatch-ui';
import type { StudyRoomData } from '../../types/studyRooms';
import { roomTopic, roomWhen } from './roomTime';

interface StudyRoomPreviewProps {
  room: StudyRoomData;
  studentNumber: number;
  // the tutor's rating in that subject, only present when the detail was fetched
  rating?: number | null;
  ratingCount?: number;
  onToggleMembership: (room: StudyRoomData) => void;
  // what the tutor can do with their own room, under the card (#50)
  actions?: ReactNode;
  // how many came to a Completed room, once the tutor confirmed it (#54)
  attendees?: number;
}

export function StudyRoomPreview({ room, studentNumber, rating, ratingCount, onToggleMembership, actions, attendees }: StudyRoomPreviewProps) {
  const { t, i18n } = useTranslation();
  const titleId = useId();
  const start = new Date(room.startsAt);
  const hosting = room.tutorNumber === studentNumber;
  // the calendar's link is null unless the student hosts or already joined the room
  const joined = !hosting && room.link != null;
  const remaining = room.availablePlaces;
  const state = room.status === 'COMPLETED' ? 'completed' : hosting ? 'hosting' : joined ? 'joined' : remaining === 0 ? 'full' : 'open';
  const format = (options: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat(i18n.language, options).format(start);

  const card = (
    <Card as="article" className="study-room-preview" aria-labelledby={titleId}>
      <div className="study-room-preview__head">
        <div className="study-room-preview__date" aria-hidden>
          <b>{start.getDate()}</b>
          <span>{format({ month: 'short' })}</span>
        </div>
        <div className="study-room-preview__title">
          <span className="sm-eyebrow">{room.subjectName}</span>
          <h3 id={titleId}>{roomTopic(room)}</h3>
        </div>
        <span className="study-room-preview__status" data-state={state}>
          <i aria-hidden />{t(`studyRooms.${state}`)}
        </span>
      </div>

      <p className="study-room-preview__time">
        <Clock3 size={17} aria-hidden />
        <time dateTime={room.startsAt}>{roomWhen(room, i18n.language)}</time>
      </p>

      <ProgressBar
        label={t('studyRooms.places')}
        value={room.participantCount}
        max={room.capacity}
        showLabel
        size="sm"
        color="var(--sm-grad-brand)"
        valueText={`${room.participantCount}/${room.capacity} · ${t('studyRooms.placesLeft', { count: remaining })}`}
      />

      <footer className="study-room-preview__footer">
        <div className="study-room-preview__tutor">
          <Avatar name={room.tutorName} size={40} />
          <div>
            <span className="study-room-preview__name">{room.tutorName}</span>
            {rating != null && <Rating value={rating} count={ratingCount ?? 0} size={14} showValue readOnly />}
          </div>
        </div>
        {hosting ? (
          <span className="study-room-preview__host">
            <Users size={15} aria-hidden />{attendees === undefined ? t('studyRooms.yourRoom') : t('studyRooms.attended', { count: attendees })}
          </span>
        ) : (
          <Button
            variant={joined ? 'danger' : 'tinted'}
            size="sm"
            disabled={!joined && remaining === 0}
            onClick={() => onToggleMembership(room)}
          >
            {joined ? t('rooms.leave') : remaining === 0 ? t('studyRooms.full') : t('rooms.join')}
          </Button>
        )}
      </footer>
    </Card>
  );
  if (!actions) return card;
  return (
    <div className="study-room-preview-wrap">
      {card}
      <div className="study-room-preview__actions">{actions}</div>
    </div>
  );
}
