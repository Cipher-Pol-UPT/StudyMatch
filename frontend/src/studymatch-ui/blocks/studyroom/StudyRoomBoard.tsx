import { CalendarPlus,Video } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isBackendError } from '../../../api/errors';
import type { FormFeedbackProps } from '../../../types/forms';
import { Button } from '../../components/button/Button';
import { Select } from '../../components/field/Select';
import { Textarea } from '../../components/field/Textarea';
import { TextField } from '../../components/field/TextField';
import { NumberStepper } from '../../components/inputs/NumberStepper';
import { Banner } from '../../components/overlay/Banner';
import { Dialog } from '../../components/overlay/Dialog';
import { RadioGroup } from '../../components/selection/Radio';
import { useFormFeedback } from '../../utils/useFormFeedback';
import { StudyRoomCard,type StudyRoomCardProps } from './StudyRoomCard';
import { CAPACITY_RANGE,type StudyRoom,type StudyRoomActions,type StudyRoomPerson } from './model';

// times are local "2026-11-15T18:00", null when the date or the time is missing
export interface StudyRoomDraft {
  subjectId: number | null; startsAt: string | null; endsAt: string | null; capacity: number; link: string | null; description: string;
}
export interface CreateStudyRoomFormProps extends FormFeedbackProps {
  subjects: { id: number; name: string }[]; onSubmit: (draft: StudyRoomDraft) => Promise<void>; onCancel?: () => void; now?: Date;
  // editing a room (#50): its values filled in, the subject locked and its own Meet link, saved with Save changes
  room?: { subjectId: number; startsAt: string; endsAt: string; capacity: number; link: string | null; description: string | null };
}
// the backend's codes about one field show under it, the rest (an overlap) in the banner at the top
const CODE_FIELDS = { STUDYROOM_IN_PAST: 'startsAt', STUDYROOM_ENDS_BEFORE_START: 'endsAt', CAPACITY_BELOW_PARTICIPANTS: 'capacity' } as const;
const dateInput = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
export function CreateStudyRoomForm({ subjects, onSubmit, onCancel, now = new Date(), fieldErrors, room }: CreateStudyRoomFormProps) {
  const { t } = useTranslation();
  const feedback = useFormFeedback(fieldErrors, CODE_FIELDS);
  const [subjectId, setSubjectId] = useState(room ? String(room.subjectId) : subjects[0] ? String(subjects[0].id) : '');
  // the backend's times are local "2026-11-15T18:00:00"
  const [date, setDate] = useState(() => room ? room.startsAt.slice(0, 10) : dateInput(new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1)));
  const [from, setFrom] = useState(room ? room.startsAt.slice(11, 16) : '18:00');
  const [to, setTo] = useState(room ? room.endsAt.slice(11, 16) : '19:00');
  const [capacity, setCapacity] = useState(room?.capacity ?? 8);
  const [linkMode, setLinkMode] = useState<'auto' | 'own'>(room ? 'own' : 'auto');
  const [link, setLink] = useState(room?.link ?? '');
  const [description, setDescription] = useState(room?.description ?? '');
  const at = (time: string) => date && time ? `${date}T${time}` : null;
  return <form className="sm-room-form" noValidate onSubmit={event => {
    event.preventDefault();
    void feedback.submit(async () => {
      try {
        await onSubmit({ subjectId: subjectId ? Number(subjectId) : null, startsAt: at(from), endsAt: at(to),
          capacity, link: linkMode === 'auto' ? null : link.trim(), description: description.trim() });
      } catch (error) {
        // Google couldn't make it, so the tutor can paste one straight away
        if (isBackendError(error) && error.code === 'MEET_LINK_UNAVAILABLE') setLinkMode('own');
        throw error;
      }
    });
  }}>
    {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
    <Select label={t('fields.subject')} value={subjectId} hint={t(room ? 'rooms.subjectLocked' : 'rooms.subjectHint')} disabled={!!room || feedback.busy}
      error={feedback.fieldError('subjectId')} options={subjects.map(subject => ({ value: String(subject.id), label: subject.name }))}
      onChange={event => setSubjectId(event.target.value)} />
    <div className="sm-room-form__row">
      <TextField label={t('rooms.date')} type="date" value={date} min={dateInput(now)} disabled={feedback.busy}
        error={feedback.fieldError('startsAt')} onChange={event => setDate(event.target.value)} />
      <TextField label={t('rooms.start')} type="time" value={from} disabled={feedback.busy} onChange={event => setFrom(event.target.value)} />
      <TextField label={t('rooms.end')} type="time" value={to} disabled={feedback.busy}
        error={feedback.fieldError('endsAt')} onChange={event => setTo(event.target.value)} />
    </div>
    <div className="sm-room-form__box">
      <NumberStepper label={t('rooms.places')} hint={t('rooms.placesHint', CAPACITY_RANGE)} value={capacity} onValueChange={setCapacity}
        min={CAPACITY_RANGE.min} max={CAPACITY_RANGE.max} disabled={feedback.busy} error={feedback.fieldError('capacity')} />
    </div>
    {!room && <RadioGroup label={t('rooms.meetChoice')} variant="cards" orientation="horizontal" value={linkMode} disabled={feedback.busy}
      onValueChange={value => setLinkMode(value as 'auto' | 'own')} options={[
        { value: 'auto', label: t('rooms.meetAuto'), description: t('rooms.meetAutoHint') },
        { value: 'own', label: t('rooms.meetOwn'), description: t('rooms.meetOwnHint') }]} />}
    {linkMode === 'own' && <TextField label={t('rooms.meetLink')} icon={Video} type="url" placeholder="https://meet.google.com/abc-defg-hij" value={link}
      disabled={feedback.busy} error={feedback.fieldError('link')} onChange={event => setLink(event.target.value)} />}
    <Textarea label={t('fields.description')} optional showCount maxLength={500} placeholder={t('rooms.descriptionPlaceholder')} value={description}
      disabled={feedback.busy} error={feedback.fieldError('description')} onChange={event => setDescription(event.target.value)} />
    <div className="sm-room-form__actions">
      {onCancel && <Button variant="secondary" onClick={onCancel} disabled={feedback.busy}>{t('common.cancel')}</Button>}
      <Button type="submit" icon={CalendarPlus} loading={feedback.busy}>{t(room ? 'rooms.save' : 'rooms.create')}</Button>
    </div>
  </form>;
}
export interface StudyRoomDetailsProps extends StudyRoomCardProps { onCancel?: (room: StudyRoom) => void; onConfirmAttendance?: (room: StudyRoom) => void }
export function StudyRoomDetails({ onCancel, onConfirmAttendance, ...props }: StudyRoomDetailsProps) {
  const { t } = useTranslation();
  return <div><StudyRoomCard {...props} />
    {props.canCancel && onCancel && <Button onClick={() => onCancel(props.room)}>{t('common.cancel')}</Button>}
    {props.canConfirmAttendance && onConfirmAttendance && <Button onClick={() => onConfirmAttendance(props.room)}>{t('rooms.attendance')}</Button>}
  </div>;
}
export interface StudyRoomBoardProps {
  rooms: StudyRoom[]; actions: Record<string, StudyRoomActions>;
  onJoin?: (room: StudyRoom) => void; onLeave?: (room: StudyRoom) => void; onEnter?: (room: StudyRoom) => void; onSelect?: (room: StudyRoom) => void;
}
export function StudyRoomBoard({ rooms, actions, ...callbacks }: StudyRoomBoardProps) {
  const { t } = useTranslation();
  return <section aria-label={t('rooms.title')}>{rooms.map(room => <StudyRoomCard key={room.id} room={room} {...actions[room.id]} {...callbacks} />)}</section>;
}
export interface ConfirmAttendanceDialogProps extends FormFeedbackProps {
  open: boolean; participants: StudyRoomPerson[]; onClose: () => void; onSubmit: (ids: string[]) => Promise<void>;
}
export function ConfirmAttendanceDialog({ open, participants, onClose, onSubmit, fieldErrors }: ConfirmAttendanceDialogProps) {
  const { t } = useTranslation(); const [selected, setSelected] = useState<string[]>([]);
  const feedback = useFormFeedback(fieldErrors);
  return <Dialog open={open} onClose={onClose} title={t('rooms.attendance')}>
    {participants.map(person => <label key={person.id}><input type="checkbox" checked={selected.includes(person.id)}
      onChange={event => setSelected(ids => event.target.checked ? [...ids,person.id] : ids.filter(id => id !== person.id))} />{person.name}</label>)}
    {feedback.fieldError('attendees') && <p role="alert">{feedback.fieldError('attendees')}</p>}
    {feedback.message && <Banner tone="danger">{feedback.message}</Banner>}
    <Button loading={feedback.busy} onClick={() => void feedback.submit(() => onSubmit(selected))}>{t('common.confirm')}</Button>
  </Dialog>;
}
