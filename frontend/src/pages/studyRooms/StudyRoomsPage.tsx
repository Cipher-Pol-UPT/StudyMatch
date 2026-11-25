import { BookOpen, CalendarDays, Check, ListChecks, Plus, Users } from 'lucide-react';
import { useEffect, useState, type CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError } from '../../api/errors';
import { createStudyRoom, getStudyRoom, getStudyRooms, joinStudyRoom, leaveStudyRoom } from '../../api/studyRooms';
import { getSubjects } from '../../api/subjects';
import { Button, CreateStudyRoomForm, Dialog, EmptyState, Skeleton, useToast, type StudyRoomDraft } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import type { SubjectData } from '../../types/subjects';
import type { StudyRoomData, StudyRoomDetailData } from '../../types/studyRooms';
import { dateKey, monthStart } from './calendar';
import { StudyRoomCalendar, StudyRoomMiniCalendar } from './StudyRoomCalendar';
import { StudyRoomPreview } from './StudyRoomPreview';
import { subjectColor } from './subjectColor';
import { useTutorSubjects } from './useTutorSubjects';
import './studyRoom.css';

interface StudyRoomsPageProps {
  currentUser: CurrentUser;
  initialDate?: Date;
}

type StudyRoomsView = 'calendar' | 'inscriptions' | 'created';

export function StudyRoomsPage({ currentUser, initialDate }: StudyRoomsPageProps) {
  const { t } = useTranslation();
  const [today] = useState(() => initialDate ?? new Date());
  const [rooms, setRooms] = useState<StudyRoomData[]>([]);
  const [subjects, setSubjects] = useState<SubjectData[]>([]);
  const [loading, setLoading] = useState(true);
  const [month, setMonth] = useState(() => monthStart(today));
  const [view, setView] = useState<StudyRoomsView>('calendar');
  const [subjectId, setSubjectId] = useState<number | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<StudyRoomDetailData | null>(null);
  const [selectedDay, setSelectedDay] = useState<Date | null>(null);
  const [notice, setNotice] = useState('');
  const toast = useToast();
  const tutorSubjects = useTutorSubjects();
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    let alive = true;
    getStudyRooms()
      .then((data) => alive && setRooms(data))
      .catch(() => alive && setRooms([]))
      .finally(() => alive && setLoading(false));
    getSubjects()
      .then((data) => alive && setSubjects(data))
      .catch(() => alive && setSubjects([]));
    return () => {
      alive = false;
    };
  }, []);

  // the tutor's rating and canJoin live in the detail, fetched when a room is opened
  useEffect(() => {
    if (selectedId == null) return;
    let alive = true;
    getStudyRoom(selectedId)
      .then((data) => alive && setDetail(data))
      .catch(() => alive && setDetail(null));
    return () => {
      alive = false;
    };
  }, [selectedId]);

  const studentNumber = currentUser.number;
  // the list's link is null unless the student hosts the room or already joined it
  const joinedRooms = rooms.filter((room) => room.tutorNumber !== studentNumber && room.link != null);
  const createdRooms = rooms.filter((room) => room.tutorNumber === studentNumber);
  const filteredRooms = rooms.filter((room) => subjectId == null || room.subjectId === subjectId);
  const selectedRoom = filteredRooms.find((room) => room.id === selectedId);
  const personalRooms = (view === 'created' ? createdRooms : joinedRooms)
    .filter((room) => subjectId == null || room.subjectId === subjectId);

  // clears the stale detail first, so the old room's rating never flashes while the new one loads
  const openRoom = (roomId: number | null) => {
    setDetail(null);
    setSelectedId(roomId);
  };

  const selectSubject = (id: number | null) => {
    setSubjectId(id);
    openRoom(null);
  };

  const changeMonth = (date: Date) => {
    setMonth(monthStart(date));
    openRoom(null);
    setSelectedDay(null);
  };

  // the backend checks everything, if it refuses the error goes back to the form
  const createRoom = async (draft: StudyRoomDraft) => {
    const created = await createStudyRoom({ ...draft, description: draft.description || null });
    setRooms((previous) => [...previous, created].sort((a, b) => a.startsAt.localeCompare(b.startsAt)));
    setCreating(false);
    setView('calendar');
    changeMonth(new Date(created.startsAt));
    toast.success(t('rooms.created'));
  };

  const selectRoom = (room: StudyRoomData) => {
    document.getElementById(`study-room-event-${room.id}`)?.focus();
    openRoom(room.id);
    setSelectedDay(new Date(room.startsAt));
  };

  const toggleMembership = async (room: StudyRoomData) => {
    if (room.tutorNumber === studentNumber) return;
    const joined = room.link != null;
    try {
      const updated = joined ? await leaveStudyRoom(room.id) : await joinStudyRoom(room.id);
      setRooms((previous) => previous.map((item) => (item.id === updated.id ? updated : item)));
      setDetail((previous) => previous?.id === updated.id ? { ...previous, ...updated, joined: !joined, hosted: false } : previous);
      setNotice(t(joined ? 'studyRooms.leftNotice' : 'studyRooms.joinedNotice', { topic: updated.description ?? updated.subjectName }));
    } catch (error) {
      toast.danger(isBackendError(error) ? t(`errors.${error.code}`) : t('studyRooms.membershipFailed'));
    }
  };

  return (
    <div className="study-rooms">
      <header className="study-rooms__heading">
          <span className="study-rooms__eyebrow"><BookOpen size={15} aria-hidden />StudyMatch</span>
          <h1>{t('studyRooms.heading')}</h1>
          <p>{t('studyRooms.intro')}</p>
          <span className="study-rooms__hint"><CalendarDays size={16} aria-hidden />{t('studyRooms.selectHint')}</span>
          {tutorSubjects.length > 0 && (
            <Button icon={Plus} className="study-rooms__create" onClick={() => setCreating(true)}>{t('rooms.newRoom')}</Button>
          )}
      </header>

      <Dialog open={creating} onClose={() => setCreating(false)} title={t('rooms.newRoom')} description={t('rooms.newRoomDescription')} size="lg">
        <CreateStudyRoomForm
          subjects={tutorSubjects.map((subject) => ({ id: subject.subjectId, name: subject.subjectName }))}
          now={today}
          onCancel={() => setCreating(false)}
          onSubmit={createRoom}
        />
      </Dialog>

      <Dialog open={view === 'calendar' && !!selectedRoom} onClose={() => openRoom(null)} title={t('studyRooms.selectedRoom')} className="study-rooms-dialog">
        {selectedRoom && (
          <StudyRoomPreview
            room={detail ?? selectedRoom}
            studentNumber={studentNumber}
            rating={detail?.tutorAverageRating}
            ratingCount={detail?.tutorRatingCount}
            onToggleMembership={toggleMembership}
          />
        )}
      </Dialog>

      <p className="sm-visually-hidden" role="status">{notice}</p>

      <div className="study-rooms__board">
        <aside className="study-rooms__sidebar" aria-label={t('studyRooms.filters')}>
          <fieldset className="study-rooms__filters">
            <legend>{t('studyRooms.filters')}</legend>
            <p>{t('studyRooms.filterHint')}</p>
            {subjects.map((subject) => (
              <button key={subject.id} type="button" className="study-rooms__subject" aria-pressed={subjectId === subject.id} onClick={() => selectSubject(subject.id)} style={{ '--subject-color': subjectColor(subject.id) } as CSSProperties}>
                <i aria-hidden />
                <span>{subject.name}</span>
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
              <button key={key} type="button" aria-pressed={view === key} onClick={() => { openRoom(null); setView(key); }}>
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
              openRoom(filteredRooms.find((room) => dateKey(new Date(room.startsAt)) === dateKey(day))?.id ?? null);
              setView('calendar');
            }}
          />
        </aside>

        <div className="study-rooms__content">
          {loading ? (
            <div className="study-rooms__loading" aria-label={t('studyRooms.loading')}>
              <Skeleton />
              <Skeleton />
              <Skeleton />
            </div>
          ) : view === 'calendar' ? (
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
                  {personalRooms.map((room) => <StudyRoomPreview key={room.id} room={room} studentNumber={studentNumber} onToggleMembership={toggleMembership} />)}
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
