import type { StudyRoomData } from '../../types/studyRooms';

// "Wed, Dec 2 · 14:00–15:30", the way a room's card and its dialogs say when it is
export function roomWhen(room: Pick<StudyRoomData, 'startsAt' | 'endsAt'>, locale: string) {
  const day = new Intl.DateTimeFormat(locale, { weekday: 'short', day: 'numeric', month: 'short' });
  const time = new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', hour12: false });
  return `${day.format(new Date(room.startsAt))} · ${time.format(new Date(room.startsAt))}–${time.format(new Date(room.endsAt))}`;
}

// what a room is called: its description, or its subject without one
export const roomTopic = (room: Pick<StudyRoomData, 'description' | 'subjectName'>) => room.description ?? room.subjectName;
