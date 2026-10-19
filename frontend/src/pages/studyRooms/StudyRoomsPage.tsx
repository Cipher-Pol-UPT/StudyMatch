import { BookOpen, CalendarDays, Check, ListChecks, Users } from 'lucide-react';
import { useState, type CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Dialog, EmptyState } from '../../studymatch-ui';
import { createDemoStudyRooms, dateKey, DEMO_SUBJECTS, demoStudent, monthStart, type DemoStudent, type DemoStudyRoom, type StudyRoomsView, type SubjectId } from './demoStudyRooms';
import { StudyRoomCalendar, StudyRoomMiniCalendar } from './StudyRoomCalendar';
import { StudyRoomPreview } from './StudyRoomPreview';
import './studyRoom.css';

interface StudyRoomsPageProps {
  currentUser: DemoStudent;
  initialDate?: Date;
}

export function StudyRoomsPage({ currentUser, initialDate }: StudyRoomsPageProps) {
  const { t } = useTranslation();
  const [today] = useState(() => initialDate ?? new Date());
  const [rooms, setRooms] = useState(() => createDemoStudyRooms(currentUser, today));
  const [month, setMonth] = useState(() => monthStart(today));
  const [view, setView] = useState<StudyRoomsView>('calendar');
  const [subjectId, setSubjectId] = useState<SubjectId | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selectedDay, setSelectedDay] = useState<Date | null>(null);
  const [notice, setNotice] = useState('');
  const student = demoStudent(currentUser);

  const joinedRooms = rooms.filter((room) => room.participants.some((person) => person.id === student.id));
  const createdRooms = rooms.filter((room) => room.tutor.id === student.id);
  const filteredRooms = rooms.filter((room) => subjectId === null || room.subjectId === subjectId);
  const selectedRoom = filteredRooms.find((room) => room.id === selectedId);
  const personalRooms = (view === 'created' ? createdRooms : joinedRooms).filter((room) => subjectId === null || room.subjectId === subjectId);

  const selectSubject = (id: SubjectId | null) => {
    setSubjectId(id);
    setSelectedId(null);
  };

  const changeMonth = (date: Date) => {
    setMonth(monthStart(date));
    setSelectedId(null);
    setSelectedDay(null);
  };

  const selectRoom = (room: DemoStudyRoom) => {
    document.getElementById(`study-room-event-${room.id}`)?.focus();
    setSelectedId(room.id);
    setSelectedDay(room.start);
  };

  const toggleMembership = (room: DemoStudyRoom) => {
    if (room.tutor.id === student.id) return;
    const joined = room.participants.some((person) => person.id === student.id);
    if (!joined && room.participants.length >= room.capacity) return;
    setRooms((previous) => previous.map((item) => item.id !== room.id ? item : {
      ...item,
      participants: joined ? item.participants.filter((person) => person.id !== student.id) : [...item.participants, student],
    }));
    setNotice(t(joined ? 'studyRooms.leftNotice' : 'studyRooms.joinedNotice', { topic: room.topic }));
  };

  return (
    <div className="study-rooms">
      <header className="study-rooms__heading">
          <span className="study-rooms__eyebrow"><BookOpen size={15} aria-hidden />StudyMatch <span>{t('studyRooms.demo')}</span></span>
          <h1>{t('studyRooms.heading')}</h1>
          <p>{t('studyRooms.intro')}</p>
          <span className="study-rooms__hint"><CalendarDays size={16} aria-hidden />{t('studyRooms.selectHint')}</span>
      </header>

      <Dialog open={view === 'calendar' && !!selectedRoom} onClose={() => setSelectedId(null)} title={t('studyRooms.selectedRoom')} className="study-rooms-dialog">
        {selectedRoom && <StudyRoomPreview room={selectedRoom} studentId={student.id} onToggleMembership={toggleMembership} />}
      </Dialog>

      <p className="sm-visually-hidden" role="status">{notice}</p>

      <div className="study-rooms__board">
        <aside className="study-rooms__sidebar" aria-label={t('studyRooms.filters')}>
          <fieldset className="study-rooms__filters">
            <legend>{t('studyRooms.filters')}</legend>
            <p>{t('studyRooms.filterHint')}</p>
            {DEMO_SUBJECTS.map((subject) => (
              <button key={subject.id} type="button" className="study-rooms__subject" aria-pressed={subjectId === subject.id} onClick={() => selectSubject(subject.id)} style={{ '--subject-color': subject.color } as CSSProperties}>
                <i aria-hidden />
                <span>{t(`studyRooms.subjects.${subject.id}`)}</span>
                <Check size={14} className="study-rooms__subject-check" aria-hidden />
              </button>
            ))}
            <Button variant="ghost" size="sm" className="study-rooms__reset" aria-pressed={subjectId === null} onClick={() => selectSubject(null)}>
              {t('studyRooms.allSubjects')}
            </Button>
          </fieldset>

          <nav className="study-rooms__views" aria-label={t('studyRooms.views')}>
            <h2>{t('studyRooms.others')}</h2>
            {([
              { key: 'calendar', icon: CalendarDays, label: 'studyRooms.calendar', count: rooms.length },
              { key: 'inscriptions', icon: ListChecks, label: 'studyRooms.inscriptions', count: joinedRooms.length },
              { key: 'created', icon: Users, label: 'studyRooms.created', count: createdRooms.length },
            ] as const).map(({ key, icon: Icon, label, count }) => (
              <button key={key} type="button" aria-pressed={view === key} onClick={() => { setSelectedId(null); setView(key); }}>
                <Icon size={17} aria-hidden /><span>{t(label)}</span><small>{count}</small>
              </button>
            ))}
          </nav>

          <StudyRoomMiniCalendar
            month={month}
            onMonthChange={changeMonth}
            rooms={filteredRooms}
            selectedDay={selectedDay}
            onDaySelect={(day) => {
              setMonth(monthStart(day));
              setSelectedDay(day);
              setSelectedId(filteredRooms.find((room) => dateKey(room.start) === dateKey(day))?.id ?? null);
              setView('calendar');
            }}
          />
        </aside>

        <div className="study-rooms__content">
          {view === 'calendar' ? (
            <StudyRoomCalendar month={month} onMonthChange={changeMonth} rooms={filteredRooms} selectedRoomId={selectedRoom?.id ?? null} today={today} onSelect={selectRoom} />
          ) : (
            <section aria-labelledby="study-rooms-list-title" className="study-rooms__personal">
              <header className="study-rooms__list-heading">
                <div>
                  <h2 id="study-rooms-list-title">{t(view === 'created' ? 'studyRooms.created' : 'studyRooms.inscriptions')}</h2>
                  <p>{t(view === 'created' ? 'studyRooms.createdHint' : 'studyRooms.inscriptionsHint')}</p>
                </div>
                <span>{t('studyRooms.roomCount', { count: personalRooms.length })}</span>
              </header>
              {personalRooms.length ? (
                <div className="study-rooms__cards">
                  {personalRooms.map((room) => <StudyRoomPreview key={room.id} room={room} studentId={student.id} onToggleMembership={toggleMembership} />)}
                </div>
              ) : (
                <EmptyState icon={view === 'created' ? Users : ListChecks} title={t('studyRooms.noRooms')} body={t('studyRooms.noPersonalRoomsBody')} action={<Button variant="tinted" onClick={() => { selectSubject(null); setView('calendar'); }}>{t('studyRooms.browseRooms')}</Button>} />
              )}
            </section>
          )}
        </div>
      </div>
    </div>
  );
}