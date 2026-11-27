import { BookOpen, CalendarDays, Check, CircleX, GraduationCap, ListChecks, Pencil, Plus, Users } from 'lucide-react';
import { useEffect, useState, type CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError } from '../../api/errors';
import { cancelStudyRoom, confirmAttendance, createStudyRoom, getStudyRoom, getStudyRoomParticipants, getStudyRooms, joinStudyRoom, leaveStudyRoom, updateStudyRoom } from '../../api/studyRooms';
import { getSubjects } from '../../api/subjects';
import { Banner, Button, ConfirmAttendanceDialog, ConfirmDialog, CreateStudyRoomForm, Dialog, EmptyState, Skeleton, useToast, type StudyRoomDraft } from '../../studymatch-ui';
import type { CurrentUser } from '../../types/currentUser';
import type { ParticipantData, StudyRoomData, StudyRoomDetailData, SubjectData } from '../../types/studyRooms';
import { BecomeTutor } from './BecomeTutor';
import { dateKey, monthStart } from './calendar';
import { roomTopic, roomWhen } from './roomTime';
import { StudyRoomCalendar, StudyRoomMiniCalendar } from './StudyRoomCalendar';
import { StudyRoomPreview } from './StudyRoomPreview';
import { subjectColor } from './subjectColor';
import { useTutorSubjects } from './useTutorSubjects';
import './studyRoom.css';

interface StudyRoomsPageProps {
  currentUser: CurrentUser;
  initialDate?: Date;
}

type StudyRoomsView = 'calendar' | 'inscriptions' | 'created' | 'tutor';

export function StudyRoomsPage({ currentUser, initialDate }: StudyRoomsPageProps) {
  const { t, i18n } = useTranslation();
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
  // a joined room waiting for Leave to be confirmed, and the room a join clashed with (#44)
  const [leaving, setLeaving] = useState<StudyRoomData | null>(null);
  const [clash, setClash] = useState<{ roomId: number; text: string } | null>(null);
  // the rooms being joined right now, so a double click sends one POST
  const [joining, setJoining] = useState<ReadonlySet<number>>(() => new Set());
  const toast = useToast();
  const tutorSubjects = useTutorSubjects();
  const [creating, setCreating] = useState(false);
  // the hosted room being edited or waiting for Cancel to be confirmed (#50)
  const [editing, setEditing] = useState<StudyRoomData | null>(null);
  // the edit form can't be closed while it saves, so its answer is never lost
  const [saving, setSaving] = useState(false);
  const [cancelling, setCancelling] = useState<StudyRoomData | null>(null);
  // rooms the backend said have started or closed, though this page still thought they hadn't
  const [locked, setLocked] = useState<ReadonlySet<number>>(() => new Set());
  // an ended room whose attendance is being confirmed, with who joined it (#54)
  const [confirming, setConfirming] = useState<{ room: StudyRoomData; participants: ParticipantData[] } | null>(null);
  // how many came, for the rooms completed while the page is open
  const [attendees, setAttendees] = useState<ReadonlyMap<number, number>>(() => new Map());
  // the time, a minute at a time, so a room's actions go when it starts while the page is open
  // a fixed date (the tests) is the page's clock until the first tick
  const [now, setNow] = useState(() => initialDate?.getTime() ?? Date.now());
  useEffect(() => {
    const tick = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(tick);
  }, []);

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
  // an overlap banner belongs to the card it was shown on, so it goes when the card closes
  const openRoom = (roomId: number | null) => {
    setDetail(null);
    setSelectedId(roomId);
    setClash(null);
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

  // a tutor's own room can change until it starts, while it's Scheduled or Full
  const canManage = (room: StudyRoomData) => room.tutorNumber === studentNumber && !locked.has(room.id)
    && (room.status === 'SCHEDULED' || room.status === 'FULL') && new Date(room.startsAt).getTime() > now;

  const replaceRoom = (updated: StudyRoomData) => {
    // a new date can move it, so the list stays in start order like after creating one
    setRooms((previous) => previous.map((item) => (item.id === updated.id ? updated : item)).sort((a, b) => a.startsAt.localeCompare(b.startsAt)));
    setDetail((previous) => previous?.id === updated.id ? { ...previous, ...updated } : previous);
  };

  // it started or closed meanwhile: say so, and its actions go
  const tooLate = (room: StudyRoomData, error: unknown) => {
    if (!isBackendError(error) || (error.code !== 'STUDYROOM_ALREADY_STARTED' && error.code !== 'STUDYROOM_CLOSED')) return false;
    toast.danger(t(`errors.${error.code}`));
    setLocked((previous) => new Set(previous).add(room.id));
    return true;
  };

  // anything else goes back to the form, under its field or in its banner
  const saveRoom = async (draft: StudyRoomDraft) => {
    if (!editing) return;
    const room = editing;
    // only closes the form if it's still this room's
    const close = () => setEditing((current) => current?.id === room.id ? null : current);
    setSaving(true);
    try {
      replaceRoom(await updateStudyRoom(room.id, {
        startsAt: draft.startsAt, endsAt: draft.endsAt, capacity: draft.capacity, link: draft.link || null, description: draft.description || null,
      }));
      close();
      toast.success(t('rooms.saved'));
    } catch (error) {
      if (!tooLate(room, error)) throw error;
      close();
    } finally {
      setSaving(false);
    }
  };

  // the reason goes to the participants; any other failure shows in the dialog
  const cancelRoom = async (reason: string) => {
    if (!cancelling) return;
    try {
      await cancelStudyRoom(cancelling.id, reason || null);
      setRooms((previous) => previous.filter((item) => item.id !== cancelling.id));
      if (selectedId === cancelling.id) openRoom(null);
      toast.success(t('rooms.cancelled'));
    } catch (error) {
      if (!tooLate(cancelling, error)) throw error;
    }
  };

  // the tutor's rooms that ended and still wait for who came, until they're Completed (48 hours at most)
  const toConfirm = createdRooms.filter((room) => room.status !== 'COMPLETED' && room.status !== 'CANCELLED'
    && new Date(room.endsAt).getTime() <= now);

  const openAttendance = async (room: StudyRoomData) => {
    try {
      setConfirming({ room, participants: await getStudyRoomParticipants(room.id) });
    } catch (error) {
      toast.danger(isBackendError(error) ? t(`errors.${error.code}`) : t('common.unexpectedError'));
    }
  };

  // already confirmed, or past the 48 hours: it's Completed, with however many the backend has as attended
  const completed = (room: StudyRoomData) => {
    replaceRoom({ ...room, status: 'COMPLETED' });
    getStudyRoomParticipants(room.id)
      .then((participants) => setAttendees((previous) => new Map(previous).set(room.id, participants.filter((p) => p.attended).length)))
      .catch(() => undefined);
  };

  // not ended yet or already closed: a toast and the dialog closes; anything else stays in the dialog
  const submitAttendance = async (ids: string[]) => {
    if (!confirming) return;
    const { room } = confirming;
    try {
      replaceRoom(await confirmAttendance(room.id, ids.map(Number)));
      setAttendees((previous) => new Map(previous).set(room.id, ids.length));
      toast.success(t('rooms.attendanceConfirmed'));
    } catch (error) {
      if (!isBackendError(error) || (error.code !== 'STUDYROOM_NOT_ENDED' && error.code !== 'STUDYROOM_CLOSED')) throw error;
      toast.danger(t(`errors.${error.code}`));
      if (error.code === 'STUDYROOM_CLOSED') completed(room);
    }
    setConfirming(null);
  };

  const manage = (room: StudyRoomData) => canManage(room) && (
    <>
      <Button variant="secondary" icon={Pencil} onClick={() => { openRoom(null); setEditing(room); }}>{t('rooms.edit')}</Button>
      <Button variant="danger" icon={CircleX} onClick={() => { openRoom(null); setCancelling(room); }}>{t('rooms.cancel')}</Button>
    </>
  );

  const selectRoom = (room: StudyRoomData) => {
    document.getElementById(`study-room-event-${room.id}`)?.focus();
    openRoom(room.id);
    setSelectedDay(new Date(room.startsAt));
  };

  const started = (room: StudyRoomData) => locked.has(room.id) || new Date(room.startsAt).getTime() <= now;

  // the room as the backend has it now, with your place in it or not
  const refresh = (room: StudyRoomData) => {
    getStudyRoom(room.id).then((fresh) => {
      setRooms((previous) => previous.map((item) => (item.id === fresh.id ? fresh : item)));
      setDetail((previous) => previous?.id === fresh.id ? fresh : previous);
    }, () => undefined);
  };

  const joinedOrLeft = (updated: StudyRoomData, joined: boolean) => {
    setRooms((previous) => previous.map((item) => (item.id === updated.id ? updated : item)));
    setDetail((previous) => previous?.id === updated.id ? { ...previous, ...updated, joined, hosted: false } : previous);
    toast.success(t(joined ? 'studyRooms.joinedNotice' : 'studyRooms.leftNotice', { topic: roomTopic(updated) }));
  };

  // an overlap names the other room above the card; full or already joined shows the room as it is now
  const membershipFailed = (room: StudyRoomData, error: unknown) => {
    if (!isBackendError(error)) return toast.danger(t('studyRooms.membershipFailed'));
    const { code, params = {} } = error;
    if (code === 'JOINED_STUDYROOM_OVERLAP') {
      const day = new Intl.DateTimeFormat(i18n.language, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
      const time = new Intl.DateTimeFormat(i18n.language, { hour: '2-digit', minute: '2-digit', hour12: false });
      return setClash({ roomId: room.id, text: t('errors.JOINED_STUDYROOM_OVERLAP', {
        subject: params.subject, startsAt: day.format(new Date(String(params.startsAt))), endsAt: time.format(new Date(String(params.endsAt))),
      }) });
    }
    if (code === 'STUDYROOM_ALREADY_STARTED' || code === 'STUDYROOM_CLOSED') setLocked((previous) => new Set(previous).add(room.id));
    if (code === 'STUDYROOM_FULL' || code === 'ALREADY_JOINED_STUDYROOM' || code === 'NOT_A_PARTICIPANT') refresh(room);
    if (code !== 'ALREADY_JOINED_STUDYROOM') toast.danger(t(`errors.${code}`, params));
  };

  const join = async (room: StudyRoomData) => {
    if (joining.has(room.id)) return;
    setClash(null);
    setJoining((previous) => new Set(previous).add(room.id));
    try {
      joinedOrLeft(await joinStudyRoom(room.id), true);
    } catch (error) {
      membershipFailed(room, error);
    } finally {
      setJoining((previous) => { const next = new Set(previous); next.delete(room.id); return next; });
    }
  };

  const leave = async () => {
    if (!leaving) return;
    try {
      joinedOrLeft(await leaveStudyRoom(leaving.id), false);
      // the room it clashed with may be this one, so the banner no longer holds
      setClash(null);
    } catch (error) {
      membershipFailed(leaving, error);
    }
  };

  // the list's link is only there once you joined; leaving asks first
  const toggleMembership = (room: StudyRoomData) => {
    if (room.tutorNumber === studentNumber || started(room)) return;
    if (room.link != null) setLeaving(room);
    else void join(room);
  };

  const preview = (room: StudyRoomData, extra: Partial<Parameters<typeof StudyRoomPreview>[0]> = {}) => (
    <StudyRoomPreview key={room.id} room={room} studentNumber={studentNumber} onToggleMembership={toggleMembership}
      started={started(room)} joining={joining.has(room.id)} actions={manage(room)} notice={clash?.roomId === room.id ? <Banner tone="danger">{clash.text}</Banner> : undefined} {...extra} />
  );

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

      <Dialog open={editing !== null} onClose={() => { if (!saving) setEditing(null); }} title={t('rooms.editRoom')} description={t('rooms.editRoomDescription')} size="lg">
        {editing && (
          <CreateStudyRoomForm
            subjects={[{ id: editing.subjectId, name: editing.subjectName }]}
            room={editing}
            now={today}
            onCancel={() => setEditing(null)}
            onSubmit={saveRoom}
          />
        )}
      </Dialog>

      <ConfirmDialog
        open={cancelling !== null}
        onClose={() => setCancelling(null)}
        tone="danger"
        title={t('rooms.cancelTitle')}
        description={cancelling && t(cancelling.participantCount ? 'rooms.cancelBody' : 'rooms.cancelBodyEmpty', {
          room: `${roomTopic(cancelling)} · ${roomWhen(cancelling, i18n.language)}`, count: cancelling.participantCount,
        })}
        reason={{ label: t('rooms.cancelReason'), placeholder: t('rooms.cancelReasonPlaceholder'), maxLength: 500 }}
        confirmLabel={t('rooms.cancel')}
        cancelLabel={t('rooms.keep')}
        onConfirm={cancelRoom}
      />

      {confirming && (
        <ConfirmAttendanceDialog
          key={confirming.room.id}
          open
          onClose={() => setConfirming(null)}
          description={`${roomTopic(confirming.room)} · ${roomWhen(confirming.room, i18n.language)}`}
          participants={confirming.participants.map((person) => ({ id: String(person.number), name: person.name, avatarUrl: `/api/students/${person.number}/pfp` }))}
          onSubmit={submitAttendance}
        />
      )}

      <Dialog open={view === 'calendar' && !!selectedRoom} onClose={() => openRoom(null)} title={t('studyRooms.selectedRoom')} className="study-rooms-dialog">
        {selectedRoom && (
          preview(detail ?? selectedRoom, { rating: detail?.tutorAverageRating, ratingCount: detail?.tutorRatingCount, actions: manage(selectedRoom) })
        )}
      </Dialog>

      <ConfirmDialog
        open={leaving !== null}
        onClose={() => setLeaving(null)}
        tone="danger"
        title={t('rooms.leaveTitle')}
        description={leaving && t('rooms.leaveBody', { room: `${roomTopic(leaving)} · ${roomWhen(leaving, i18n.language)}` })}
        confirmLabel={t('rooms.leave')}
        cancelLabel={t('rooms.stay')}
        onConfirm={leave}
      />

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
              // Apply for tutoring (#40), no count: it's the subjects you passed, not rooms
              { key: 'tutor', icon: GraduationCap, label: 'tutoring.heading', count: undefined },
            ] as const).map(({ key, icon: Icon, label, count }) => (
              <button key={key} type="button" aria-pressed={view === key} onClick={() => { openRoom(null); setView(key); }}>
                <Icon size={17} aria-hidden /><span>{t(label)}</span>{count !== undefined && <small>{count}</small>}
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
          {view === 'tutor' ? (
            <BecomeTutor />
          ) : loading ? (
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
              {view === 'created' && toConfirm.map((room) => (
                <Banner key={room.id} tone="warning" title={t('rooms.ended', { topic: roomTopic(room) })}
                  action={<Button onClick={() => void openAttendance(room)}>{t('rooms.attendance')}</Button>}>
                  {t('rooms.endedBody')}
                </Banner>
              ))}
              {personalRooms.length ? (
                <div className="study-rooms__cards">
                  {personalRooms.map((room) => preview(room, { attendees: attendees.get(room.id) }))}
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
