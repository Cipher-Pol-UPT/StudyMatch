import { CalendarPlus, Check, Clock, Copy, Lock, Plus, SearchX, Users, Video } from 'lucide-react';
import { useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { Avatar, AvatarStack } from '../../components/avatar/Avatar';
import { Button } from '../../components/button/Button';
import { Checkbox } from '../../components/selection/Checkbox';
import { Chip, ChipGroup } from '../../components/chip/Chip';
import { TagInput } from '../../components/chip/TagInput';
import { EmptyState } from '../../components/empty/EmptyState';
import { SearchField } from '../../components/field/SearchField';
import { Select } from '../../components/field/Select';
import { Textarea } from '../../components/field/Textarea';
import { TextField } from '../../components/field/TextField';
import { NumberStepper } from '../../components/inputs/NumberStepper';
import { Banner } from '../../components/overlay/Banner';
import { ConfirmDialog, Dialog, Sheet } from '../../components/overlay/Dialog';
import { useToast } from '../../components/overlay/Toast';
import { Rating } from '../../components/rating/Rating';
import { RadioGroup } from '../../components/selection/Radio';
import { Segmented } from '../../components/segmented/Segmented';
import { CAPACITY_RANGE, formatCountdown, MEET_URL_PATTERN, studyRoomAccess, toDate, useNow, type StudyRoom } from './model';
import { MeetButton, StudyRoomCard, StudyRoomStatusPill } from './StudyRoomCard';
import './studyroom.css';

export interface StudyRoomUser {
  id: string;
  name: string;
  /** Subjects with an active Tutor Role. Non-empty = can create StudyRooms. */
  tutorSubjects?: string[];
}

export interface StudyRoomDraft {
  subject: string;
  topic: string;
  topics: string[];
  description: string;
  start: Date;
  end: Date;
  capacity: number;
  /** Set when the tutor pasted their own link; otherwise the backend creates one. */
  meetUrl?: string;
}

const fmt = (locale: string, o: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat(locale, o);

// ─── Details ───────────────────────────────────────────────
export interface StudyRoomDetailsProps {
  room: StudyRoom;
  me: StudyRoomUser;
  joinedRooms?: StudyRoom[];
  onJoin?: (room: StudyRoom) => void;
  onLeave?: (room: StudyRoom) => void;
  onCancel?: (room: StudyRoom) => void;
  onConfirmAttendance?: (room: StudyRoom) => void;
  onEnter?: (room: StudyRoom) => void;
  /** Eligibility check (enrolled in the subject and not passed). Return a message to block joining. */
  eligibility?: (room: StudyRoom) => string | undefined;
  now?: Date;
  locale?: string;
}

/** Everything about one StudyRoom, with the right action for the user and the moment. */
export function StudyRoomDetails({ room, me, joinedRooms, onJoin, onLeave, onCancel, onConfirmAttendance, onEnter, eligibility, now: fixedNow, locale = 'en-GB' }: StudyRoomDetailsProps) {
  const toast = useToast();
  const now = useNow(15_000, fixedNow);
  const a = studyRoomAccess(room, me.id, now, joinedRooms);
  const start = toDate(room.start);
  const end = toDate(room.end);
  const minutes = Math.round((end.getTime() - start.getTime()) / 60_000);
  const notEligible = eligibility?.(room);
  const blocked = a.joinBlocked ?? notEligible;
  const open = room.status === 'scheduled' || room.status === 'full';
  const showLink = (a.joined || a.isHost) && open;

  return (
    <div className="sm-room-details">
      <div className="sm-room-details__head">
        <span className="sm-eyebrow">{room.subject}</span>
        <StudyRoomStatusPill room={room} access={a} now={now} />
      </div>
      <h3 className="sm-h3">{room.topic}</h3>
      <ul className="sm-room__facts" style={{ marginTop: 4 }}>
        <li>
          <Clock size={15} aria-hidden /> {fmt(locale, { weekday: 'long', day: 'numeric', month: 'long' }).format(start)} · {fmt(locale, { hour: '2-digit', minute: '2-digit' }).format(start)}–
          {fmt(locale, { hour: '2-digit', minute: '2-digit' }).format(end)} ({minutes} min)
        </li>
        <li>
          <Video size={15} aria-hidden /> Online · Google Meet
        </li>
      </ul>

      {open && a.phase !== 'ended' && (
        <div className="sm-room-details__cta">
          {a.canEnter ? (
            <>
              <MeetButton href={room.meetUrl} size="lg" block onEnter={() => onEnter?.(room)}>
                {a.isHost ? 'Start StudyRoom' : 'Enter StudyRoom'}
              </MeetButton>
              <span className="sm-caption">{a.phase === 'live' ? 'Live now. Google Meet opens in a new tab.' : `Starts ${formatCountdown(start, now)}. You can already go in.`}</span>
            </>
          ) : a.joined || a.isHost ? (
            <>
              <Button size="lg" block disabled icon={Video}>
                Opens {formatCountdown(a.opensAt, now)}
              </Button>
              <span className="sm-caption">The Enter button turns on 10 minutes before the start.</span>
            </>
          ) : a.canJoin ? (
            <>
              <Button size="lg" block disabled={!!blocked} onClick={() => onJoin?.(room)}>
                {a.seatsLeft === 0 ? 'Full' : `Join · ${a.seatsLeft} place${a.seatsLeft === 1 ? '' : 's'} left`}
              </Button>
              {blocked && <Banner tone="warning">{blocked}</Banner>}
            </>
          ) : null}
        </div>
      )}

      {showLink && (
        <div className="sm-room-details__link">
          <Video size={16} aria-hidden />
          <span>{room.meetUrl.replace('https://', '')}</span>
          <button
            type="button"
            aria-label="Copy Google Meet link"
            onClick={async () => {
              try {
                await navigator.clipboard.writeText(room.meetUrl);
                toast.success('Link copied');
              } catch {
                toast.danger('Couldn’t copy the link');
              }
            }}
          >
            <Copy size={15} />
          </button>
        </div>
      )}
      {!showLink && open && (
        <p className="sm-caption sm-room-details__private">
          <Lock size={13} aria-hidden /> The Google Meet link is shown to participants once they join.
        </p>
      )}

      <section className="sm-room-details__section">
        <span className="sm-eyebrow">Tutor</span>
        <div className="sm-room__tutor">
          <Avatar name={room.tutor.name} src={room.tutor.avatarUrl} size={44} />
          <div>
            <div style={{ fontWeight: 500 }}>{room.tutor.name}</div>
            {room.tutor.rating !== undefined ? <Rating value={room.tutor.rating} readOnly size={14} showValue count={room.tutor.ratings} label={`Rating in ${room.subject}`} /> : <span className="sm-caption">No ratings yet</span>}
          </div>
        </div>
      </section>

      {(room.description || room.topics?.length) && (
        <section className="sm-room-details__section">
          <span className="sm-eyebrow">About this session</span>
          {room.description && <p style={{ margin: 0, lineHeight: 1.5 }}>{room.description}</p>}
          {room.topics && room.topics.length > 0 && (
            <ChipGroup>
              {room.topics.map((t) => (
                <Chip key={t} size="sm">
                  {t}
                </Chip>
              ))}
            </ChipGroup>
          )}
        </section>
      )}

      <section className="sm-room-details__section">
        <span className="sm-eyebrow">
          Participants · {room.participants.length}/{room.capacity}
        </span>
        {room.participants.length === 0 ? (
          <span className="sm-caption">Nobody joined yet.</span>
        ) : a.isHost ? (
          <ul className="sm-room-details__people">
            {room.participants.map((p) => (
              <li key={p.id}>
                <Avatar name={p.name} src={p.avatarUrl} size={30} /> {p.name}
              </li>
            ))}
          </ul>
        ) : (
          <AvatarStack names={room.participants.map((p) => p.name)} max={6} />
        )}
      </section>

      <div className="sm-room-details__footer">
        {a.canLeave && onLeave && (
          <Button variant="secondary" onClick={() => onLeave(room)}>
            Leave StudyRoom
          </Button>
        )}
        {a.isHost && open && a.phase !== 'live' && a.phase !== 'ended' && onCancel && (
          <Button variant="danger" onClick={() => onCancel(room)}>
            Cancel StudyRoom
          </Button>
        )}
        {a.isHost && a.phase === 'ended' && !room.attendanceConfirmed && room.status !== 'cancelled' && onConfirmAttendance && (
          <Button icon={Check} onClick={() => onConfirmAttendance(room)}>
            Confirm attendance
          </Button>
        )}
      </div>
    </div>
  );
}

// ─── Create ────────────────────────────────────────────────
const pad = (n: number) => String(n).padStart(2, '0');
const dateInput = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

export interface CreateStudyRoomFormProps {
  subjects: string[];
  /** The tutor’s own rooms, to block overlaps. */
  hostedRooms?: StudyRoom[];
  onSubmit: (draft: StudyRoomDraft) => Promise<void> | void;
  onCancel?: () => void;
  now?: Date;
}

/** Create StudyRoom (Tutor). Online only: Google Meet is created automatically, or the tutor pastes a link. */
export function CreateStudyRoomForm({ subjects, hostedRooms = [], onSubmit, onCancel, now = new Date() }: CreateStudyRoomFormProps) {
  const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 18, 0);
  const [subject, setSubject] = useState(subjects[0] ?? '');
  const [topic, setTopic] = useState('');
  const [date, setDate] = useState(dateInput(tomorrow));
  const [from, setFrom] = useState('18:00');
  const [to, setTo] = useState('19:00');
  const [topics, setTopics] = useState<string[]>([]);
  const [description, setDescription] = useState('');
  const [capacity, setCapacity] = useState(8);
  const [meetMode, setMeetMode] = useState('auto');
  const [meetUrl, setMeetUrl] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    const start = new Date(`${date}T${from}`);
    const end = new Date(`${date}T${to}`);
    if (!subject) errs.subject = 'Choose a subject.';
    if (!topic.trim()) errs.topic = 'Give the session a topic.';
    if (Number.isNaN(start.getTime())) errs.date = 'Choose a date and time.';
    else if (start <= now) errs.date = 'The date and time are in the past.';
    if (!(end > start)) errs.to = 'The end must be after the start.';
    const clash = hostedRooms.find((r) => r.status !== 'cancelled' && toDate(r.start) < end && start < toDate(r.end));
    if (!errs.date && !errs.to && clash) errs.date = `It overlaps your StudyRoom “${clash.topic}”.`;
    if (capacity < CAPACITY_RANGE.min || capacity > CAPACITY_RANGE.max) errs.capacity = `Between ${CAPACITY_RANGE.min} and ${CAPACITY_RANGE.max} participants.`;
    if (meetMode === 'own' && !MEET_URL_PATTERN.test(meetUrl.trim())) errs.meet = 'Paste a link like https://meet.google.com/abc-defg-hij';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    try {
      await onSubmit({ subject, topic: topic.trim(), topics, description: description.trim(), start, end, capacity, meetUrl: meetMode === 'own' ? meetUrl.trim() : undefined });
    } catch (err) {
      setErrors({ form: err instanceof Error ? err.message : 'Couldn’t create the StudyRoom.' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="sm-room-form" onSubmit={submit} noValidate>
      {errors.form && <Banner tone="danger">{errors.form}</Banner>}
      <Select label="Subject" value={subject} onChange={(e) => setSubject(e.target.value)} options={subjects.map((s) => ({ value: s, label: s }))} error={errors.subject} hint="Subjects where you’re an approved tutor." />
      <TextField label="Topic" placeholder="e.g. Dynamic programming, step by step" value={topic} onChange={(e) => setTopic(e.target.value)} error={errors.topic} />
      <div className="sm-room-form__row">
        <TextField label="Date" type="date" value={date} min={dateInput(now)} onChange={(e) => setDate(e.target.value)} error={errors.date} />
        <TextField label="Start" type="time" value={from} onChange={(e) => setFrom(e.target.value)} />
        <TextField label="End" type="time" value={to} onChange={(e) => setTo(e.target.value)} error={errors.to} />
      </div>
      <div className="sm-room-form__box">
        <NumberStepper label="Places" hint={`${CAPACITY_RANGE.min}–${CAPACITY_RANGE.max} participants`} value={capacity} onValueChange={setCapacity} min={CAPACITY_RANGE.min} max={CAPACITY_RANGE.max} />
      </div>
      <TagInput label="Topics" value={topics} onValueChange={setTopics} max={6} placeholder="Add topics, press Enter" />
      <Textarea label="Description" optional maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What will you cover? What should people bring?" />
      <div className="sm-room-form__box">
        <RadioGroup
          label="Google Meet"
          value={meetMode}
          onValueChange={setMeetMode}
          options={[
            { value: 'auto', label: 'Create the link automatically', description: 'A Google Meet is created with the StudyRoom and shared with participants when they join.' },
            { value: 'own', label: 'Use my own Google Meet link' },
          ]}
        />
        {meetMode === 'own' && (
          <div style={{ marginTop: 12 }}>
            <TextField label="Google Meet link" icon={Video} placeholder="https://meet.google.com/abc-defg-hij" value={meetUrl} onChange={(e) => setMeetUrl(e.target.value)} error={errors.meet} />
          </div>
        )}
      </div>
      <div className="sm-room-form__actions">
        {onCancel && (
          <Button variant="secondary" onClick={onCancel}>
            Close
          </Button>
        )}
        <Button type="submit" loading={busy} icon={CalendarPlus}>
          Create StudyRoom
        </Button>
      </div>
    </form>
  );
}

// ─── Confirm attendance ────────────────────────────────────
export function ConfirmAttendanceDialog({ room, onClose, onConfirm }: { room: StudyRoom | null; onClose: () => void; onConfirm: (room: StudyRoom, attendeeIds: string[]) => Promise<void> | void }) {
  const [present, setPresent] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  return (
    <Dialog
      open={!!room}
      onClose={onClose}
      title="Who attended?"
      description="Only confirmed attendees can rate you. A StudyRoom counts toward achievements with at least 2 attendees."
      icon={Users}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Later
          </Button>
          <Button
            loading={busy}
            onClick={async () => {
              if (!room) return;
              setBusy(true);
              await onConfirm(room, present);
              setBusy(false);
              setPresent([]);
              onClose();
            }}
          >
            Confirm {present.length} attendee{present.length === 1 ? '' : 's'}
          </Button>
        </>
      }
    >
      <div className="sm-room-attendance">
        {room?.participants.length === 0 && <span className="sm-caption">Nobody joined this StudyRoom. Confirm to close it.</span>}
        {room?.participants.map((p) => (
          <Checkbox key={p.id} checked={present.includes(p.id)} onCheckedChange={(c) => setPresent((xs) => (c ? [...xs, p.id] : xs.filter((x) => x !== p.id)))}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
              <Avatar name={p.name} size={28} /> {p.name}
            </span>
          </Checkbox>
        ))}
      </div>
    </Dialog>
  );
}

// ─── Board ─────────────────────────────────────────────────
export interface StudyRoomBoardProps {
  rooms: StudyRoom[];
  me: StudyRoomUser;
  onJoin: (room: StudyRoom) => Promise<void> | void;
  onLeave: (room: StudyRoom) => Promise<void> | void;
  onCreate?: (draft: StudyRoomDraft) => Promise<void> | void;
  onCancel?: (room: StudyRoom, reason: string) => Promise<void> | void;
  onConfirmAttendance?: (room: StudyRoom, attendeeIds: string[]) => Promise<void> | void;
  onEnter?: (room: StudyRoom) => void;
  eligibility?: (room: StudyRoom) => string | undefined;
  now?: Date;
  locale?: string;
  /** Extra content on the toolbar. */
  toolbar?: ReactNode;
}

type View = 'explore' | 'mine' | 'hosting';

/**
 * The StudyRooms page: live banner, explore / my rooms / hosting, search and subject filters,
 * rooms grouped by day, the details panel, and the tutor flows (create, cancel, confirm attendance).
 */
export function StudyRoomBoard({ rooms, me, onJoin, onLeave, onCreate, onCancel, onConfirmAttendance, onEnter, eligibility, now: fixedNow, locale = 'en-GB', toolbar }: StudyRoomBoardProps) {
  const toast = useToast();
  const now = useNow(15_000, fixedNow);
  const isTutor = (me.tutorSubjects?.length ?? 0) > 0;
  const [view, setView] = useState<View>('explore');
  const [query, setQuery] = useState('');
  const [subject, setSubject] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [leaving, setLeaving] = useState<StudyRoom | null>(null);
  const [cancelling, setCancelling] = useState<StudyRoom | null>(null);
  const [confirming, setConfirming] = useState<StudyRoom | null>(null);

  const joined = rooms.filter((r) => r.participants.some((p) => p.id === me.id));
  const hosted = rooms.filter((r) => r.tutor.id === me.id);
  const opened = rooms.find((r) => r.id === openId) ?? null;
  const subjects = [...new Set(rooms.map((r) => r.subject))].sort();

  const live = [...joined, ...hosted].find((r) => {
    const a = studyRoomAccess(r, me.id, now);
    return a.canEnter;
  });

  const list = useMemo(() => {
    const q = query.trim().toLowerCase();
    let base = view === 'explore' ? rooms.filter((r) => (r.status === 'scheduled' || r.status === 'full') && toDate(r.end) > now) : view === 'mine' ? joined : hosted;
    if (view === 'mine') base = base.filter((r) => r.status !== 'cancelled');
    return base
      .filter((r) => !subject || r.subject === subject)
      .filter((r) => !q || `${r.subject} ${r.topic} ${r.tutor.name} ${(r.topics ?? []).join(' ')}`.toLowerCase().includes(q))
      .sort((a, b) => toDate(a.start).getTime() - toDate(b.start).getTime());
  }, [rooms, view, joined, hosted, subject, query, now]);

  const groups = useMemo(() => {
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const label = (d: Date) => {
      const day = new Date(d.getFullYear(), d.getMonth(), d.getDate());
      const diff = Math.round((day.getTime() - today.getTime()) / 86_400_000);
      if (diff === 0) return 'Today';
      if (diff === 1) return 'Tomorrow';
      if (diff === -1) return 'Yesterday';
      return fmt(locale, { weekday: 'long', day: 'numeric', month: 'long' }).format(d);
    };
    const map = new Map<string, StudyRoom[]>();
    list.forEach((r) => {
      const k = label(toDate(r.start));
      map.set(k, [...(map.get(k) ?? []), r]);
    });
    return [...map.entries()];
  }, [list, now, locale]);

  const join = async (r: StudyRoom) => {
    const block = studyRoomAccess(r, me.id, now, joined).joinBlocked ?? eligibility?.(r);
    if (block) return toast.warning(block);
    await onJoin(r);
    toast.success('You joined the StudyRoom', { description: 'The Google Meet opens 10 minutes before the start.' });
  };

  return (
    <div className="sm-room-board">
      {live && (
        <div className="sm-room-live">
          <span className="sm-room-live__dot" aria-hidden />
          <div style={{ flex: 1, minWidth: 0 }}>
            <b>{studyRoomAccess(live, me.id, now).phase === 'live' ? 'Your StudyRoom is live' : `Your StudyRoom starts ${formatCountdown(toDate(live.start), now)}`}</b>
            <span>
              {live.subject} · {live.topic}
            </span>
          </div>
          <MeetButton href={live.meetUrl} onEnter={() => onEnter?.(live)} className="sm-btn--white">
            {live.tutor.id === me.id ? 'Start StudyRoom' : 'Enter StudyRoom'}
          </MeetButton>
        </div>
      )}

      <div className="sm-room-board__toolbar">
        <Segmented<View>
          label="StudyRooms view"
          value={view}
          onValueChange={setView}
          options={[
            { value: 'explore', label: 'Explore' },
            { value: 'mine', label: `Joined · ${joined.filter((r) => r.status !== 'cancelled').length}` },
            ...(isTutor ? [{ value: 'hosting' as View, label: `Hosting · ${hosted.length}` }] : []),
          ]}
        />
        <div className="sm-room-board__tools">
          <SearchField size="sm" value={query} onValueChange={setQuery} placeholder="Search topic, tutor, subject" />
          {isTutor && onCreate && (
            <Button icon={Plus} onClick={() => setCreating(true)}>
              New StudyRoom
            </Button>
          )}
          {toolbar}
        </div>
      </div>
      <ChipGroup>
        {subjects.map((s) => (
          <Chip key={s} size="sm" selected={subject === s} onClick={() => setSubject(subject === s ? null : s)}>
            {s}
          </Chip>
        ))}
      </ChipGroup>

      {groups.length === 0 ? (
        <EmptyState
          icon={SearchX}
          title={view === 'explore' ? 'No StudyRooms found' : view === 'mine' ? 'You haven’t joined any StudyRoom' : 'You’re not hosting any StudyRoom'}
          body={query || subject ? 'Try clearing the filters.' : view === 'explore' ? 'New sessions appear here as tutors create them.' : undefined}
          action={
            query || subject ? (
              <Button
                variant="secondary"
                onClick={() => {
                  setQuery('');
                  setSubject(null);
                }}
              >
                Clear filters
              </Button>
            ) : undefined
          }
        />
      ) : (
        groups.map(([day, items]) => (
          <section key={day} className="sm-room-board__day">
            <h3 className="sm-eyebrow">{day}</h3>
            <div className="sm-room-board__grid">
              {items.map((r) => (
                <StudyRoomCard key={r.id} room={r} meId={me.id} joinedRooms={joined} now={now} locale={locale} onOpen={(x) => setOpenId(x.id)} onJoin={join} onLeave={setLeaving} onEnter={onEnter} />
              ))}
            </div>
          </section>
        ))
      )}

      <Sheet open={!!opened} onClose={() => setOpenId(null)} side="right" title="StudyRoom">
        {opened && (
          <StudyRoomDetails
            room={opened}
            me={me}
            joinedRooms={joined}
            now={now}
            locale={locale}
            eligibility={eligibility}
            onJoin={join}
            onLeave={setLeaving}
            onEnter={onEnter}
            onCancel={onCancel ? setCancelling : undefined}
            onConfirmAttendance={onConfirmAttendance ? setConfirming : undefined}
          />
        )}
      </Sheet>

      <Dialog open={creating} onClose={() => setCreating(false)} title="New StudyRoom" description="Online session on Google Meet." size="lg">
        <CreateStudyRoomForm
          subjects={me.tutorSubjects ?? []}
          hostedRooms={hosted}
          now={now}
          onCancel={() => setCreating(false)}
          onSubmit={async (d) => {
            await onCreate?.(d);
            setCreating(false);
            setView('hosting');
            toast.success('StudyRoom created', { description: 'It’s on the calendar with its Google Meet link.' });
          }}
        />
      </Dialog>

      <ConfirmDialog
        open={!!leaving}
        onClose={() => setLeaving(null)}
        title="Leave this StudyRoom?"
        description="Your place goes back to other students and the tutor is notified."
        confirmLabel="Leave"
        tone="danger"
        onConfirm={async () => {
          if (!leaving) return;
          await onLeave(leaving);
          toast.info('You left the StudyRoom');
        }}
      />
      <ConfirmDialog
        open={!!cancelling}
        onClose={() => setCancelling(null)}
        title="Cancel this StudyRoom?"
        description="It’s removed from the calendar and every participant is notified."
        confirmLabel="Cancel StudyRoom"
        cancelLabel="Keep it"
        tone="danger"
        reason={{ label: 'Reason (optional)', placeholder: 'Shared with the participants' }}
        onConfirm={async (reason) => {
          if (!cancelling) return;
          await onCancel?.(cancelling, reason);
          setOpenId(null);
          toast.info('StudyRoom cancelled', { description: 'Participants were notified.' });
        }}
      />
      <ConfirmAttendanceDialog
        room={confirming}
        onClose={() => setConfirming(null)}
        onConfirm={async (r, ids) => {
          await onConfirmAttendance?.(r, ids);
          toast.success('Attendance confirmed', { description: ids.length ? 'Attendees can now rate you.' : 'Closed with no attendees.' });
        }}
      />
    </div>
  );
}
