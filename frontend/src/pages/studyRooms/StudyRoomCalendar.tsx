import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button, EmptyState, IconButton } from '../../studymatch-ui';
import type { StudyRoomData } from '../../types/studyRooms';
import { dateKey, monthDays, shiftMonth } from './calendar';
import { subjectColor } from './subjectColor';

interface MonthNavigationProps {
  month: Date;
  onMonthChange: (month: Date) => void;
}

interface StudyRoomCalendarProps extends MonthNavigationProps {
  rooms: StudyRoomData[];
  selectedRoomId: number | null;
  today: Date;
  onSelect: (room: StudyRoomData) => void;
}

function MonthNavigation({ month, onMonthChange }: MonthNavigationProps) {
  const { t } = useTranslation();
  return (
    <>
      <IconButton icon={ChevronLeft} variant="ghost" size="sm" label={t('ui.previous_month')} onClick={() => onMonthChange(shiftMonth(month, -1))} />
      <IconButton icon={ChevronRight} variant="ghost" size="sm" label={t('ui.next_month')} onClick={() => onMonthChange(shiftMonth(month, 1))} />
    </>
  );
}

const topicOf = (room: StudyRoomData) => room.description ?? room.subjectName;

export function StudyRoomCalendar({ month, onMonthChange, rooms, selectedRoomId, today, onSelect }: StudyRoomCalendarProps) {
  const { t, i18n } = useTranslation();
  const days = monthDays(month);
  const monthLabel = new Intl.DateTimeFormat(i18n.language, { month: 'long', year: 'numeric' }).format(month);
  const dateFormat = new Intl.DateTimeFormat(i18n.language, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  const timeFormat = new Intl.DateTimeFormat(i18n.language, { hour: '2-digit', minute: '2-digit', hour12: false });
  const byDay = new Map<string, StudyRoomData[]>();
  for (const room of rooms) {
    const key = dateKey(new Date(room.startsAt));
    byDay.set(key, [...(byDay.get(key) ?? []), room]);
  }
  const inMonth = rooms.filter((room) => {
    const start = new Date(room.startsAt);
    return start.getMonth() === month.getMonth() && start.getFullYear() === month.getFullYear();
  });

  return (
    <section className="study-rooms-calendar" aria-label={t('studyRooms.calendarLabel')}>
      <header className="study-rooms-calendar__toolbar">
        <div aria-live="polite">
          <h2>{monthLabel}</h2>
          <p>{t('studyRooms.roomCount', { count: inMonth.length })}</p>
        </div>
        <div className="study-rooms-calendar__navigation">
          <Button variant="secondary" size="sm" onClick={() => onMonthChange(today)}>{t('home.calendar.today')}</Button>
          <MonthNavigation month={month} onMonthChange={onMonthChange} />
        </div>
      </header>

      <div className="study-rooms-calendar__scroll" role="region" aria-label={t('studyRooms.calendarLabel')}>
        <table className="study-rooms-calendar__table">
          <caption className="sm-visually-hidden">{monthLabel}</caption>
          <thead>
            <tr>{days.slice(0, 7).map((day) => (
              <th key={day.getDay()} scope="col">{new Intl.DateTimeFormat(i18n.language, { weekday: 'short' }).format(day)}</th>
            ))}</tr>
          </thead>
          <tbody>
            {Array.from({ length: 6 }, (_, week) => (
              <tr key={week}>
                {days.slice(week * 7, week * 7 + 7).map((day) => {
                  const key = dateKey(day);
                  return (
                    <td key={key} data-outside={day.getMonth() !== month.getMonth()} data-today={key === dateKey(today)}>
                      <span className="study-rooms-calendar__day" aria-hidden>{day.getDate()}</span>
                      <span className="sm-visually-hidden">{dateFormat.format(day)}</span>
                      <div className="study-rooms-calendar__events">
                        {(byDay.get(key) ?? []).map((room) => {
                          const start = new Date(room.startsAt);
                          return (
                            <button
                              id={`study-room-event-${room.id}`}
                              key={room.id}
                              type="button"
                              className="study-rooms-calendar__event"
                              aria-label={t('studyRooms.openRoom', { topic: topicOf(room), date: dateFormat.format(day) })}
                              aria-pressed={selectedRoomId === room.id}
                              title={`${topicOf(room)} · ${timeFormat.format(start)}`}
                              onClick={() => onSelect(room)}
                            >
                              <i style={{ background: subjectColor(room.subjectId) }} aria-hidden />
                              <span><b>{topicOf(room)}</b><small>{timeFormat.format(start)}</small></span>
                            </button>
                          );
                        })}
                      </div>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {inMonth.length === 0 && <EmptyState compact icon={CalendarDays} title={t('studyRooms.noRooms')} body={t('studyRooms.noRoomsBody')} />}
    </section>
  );
}

interface StudyRoomMiniCalendarProps extends MonthNavigationProps {
  rooms: StudyRoomData[];
  selectedDay: Date | null;
  onDaySelect: (day: Date) => void;
}

export function StudyRoomMiniCalendar({ month, onMonthChange, rooms, selectedDay, onDaySelect }: StudyRoomMiniCalendarProps) {
  const { t, i18n } = useTranslation();
  const days = monthDays(month);
  const monthLabel = new Intl.DateTimeFormat(i18n.language, { month: 'long', year: 'numeric' }).format(month);
  const dateFormat = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'full' });
  const eventDays = new Set(rooms.map((room) => dateKey(new Date(room.startsAt))));

  return (
    <section className="study-rooms-mini" aria-label={t('studyRooms.monthPicker')}>
      <header>
        <span aria-live="polite">{monthLabel}</span>
        <div><MonthNavigation month={month} onMonthChange={onMonthChange} /></div>
      </header>
      <div className="study-rooms-mini__grid">
        {days.slice(0, 7).map((day) => <span key={day.getDay()} aria-hidden>{new Intl.DateTimeFormat(i18n.language, { weekday: 'narrow' }).format(day)}</span>)}
        {days.map((day) => (
          <button
            key={dateKey(day)}
            type="button"
            aria-label={dateFormat.format(day)}
            aria-pressed={!!selectedDay && dateKey(day) === dateKey(selectedDay)}
            data-outside={day.getMonth() !== month.getMonth()}
            data-event={eventDays.has(dateKey(day))}
            onClick={() => onDaySelect(day)}
          >
            {day.getDate()}
          </button>
        ))}
      </div>
    </section>
  );
}
