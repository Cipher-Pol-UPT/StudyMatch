import { Clock3, Users } from 'lucide-react';
import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { Avatar, Button, Card, ProgressBar, Rating } from '../../studymatch-ui';
import type { DemoStudyRoom } from './demoStudyRooms';

interface StudyRoomPreviewProps {
  room: DemoStudyRoom;
  studentId: string;
  onToggleMembership: (room: DemoStudyRoom) => void;
}

export function StudyRoomPreview({ room, studentId, onToggleMembership }: StudyRoomPreviewProps) {
  const { t, i18n } = useTranslation();
  const titleId = useId();
  const hosting = room.tutor.id === studentId;
  const joined = room.participants.some((person) => person.id === studentId);
  const remaining = room.capacity - room.participants.length;
  const state = hosting ? 'hosting' : joined ? 'joined' : remaining === 0 ? 'full' : 'open';
  const format = (options: Intl.DateTimeFormatOptions, date = room.start) =>
    new Intl.DateTimeFormat(i18n.language, options).format(date);

  return (
    <Card as="article" className="study-room-preview" aria-labelledby={titleId}>
      <div className="study-room-preview__head">
        <div className="study-room-preview__date" aria-hidden>
          <b>{room.start.getDate()}</b>
          <span>{format({ month: 'short' })}</span>
        </div>
        <div className="study-room-preview__title">
          <span className="sm-eyebrow">{t(`studyRooms.subjects.${room.subjectId}`)}</span>
          <h3 id={titleId}>{room.topic}</h3>
        </div>
        <span className="study-room-preview__status" data-state={state}>
          <i aria-hidden />{t(`studyRooms.${state}`)}
        </span>
      </div>

      <p className="study-room-preview__time">
        <Clock3 size={17} aria-hidden />
        <time dateTime={room.start.toISOString()}>
          {format({ weekday: 'short', day: 'numeric', month: 'short' })} · {format({ hour: '2-digit', minute: '2-digit', hour12: false })}–{format({ hour: '2-digit', minute: '2-digit', hour12: false }, room.end)}
        </time>
      </p>

      <ProgressBar
        label={t('studyRooms.places')}
        value={room.participants.length}
        max={room.capacity}
        showLabel
        size="sm"
        color="var(--sm-grad-brand)"
        valueText={`${room.participants.length}/${room.capacity} · ${t('studyRooms.placesLeft', { count: remaining })}`}
      />

      <footer className="study-room-preview__footer">
        <div className="study-room-preview__tutor">
          <Avatar name={room.tutor.name} size={40} />
          <div>
            <span className="study-room-preview__name">{room.tutor.name}</span>
            <Rating value={room.rating} count={room.ratingCount} size={14} showValue readOnly />
          </div>
        </div>
        {hosting ? (
          <span className="study-room-preview__host"><Users size={15} aria-hidden />{t('studyRooms.yourRoom')}</span>
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
}