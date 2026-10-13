import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { FormFeedbackProps } from '../../../types/forms';
import { Button } from '../../components/button/Button';
import { Banner } from '../../components/overlay/Banner';
import { Dialog } from '../../components/overlay/Dialog';
import { useFormFeedback } from '../../utils/useFormFeedback';
import { FormFields } from '../FormFields';
import { StudyRoomCard,type StudyRoomCardProps } from './StudyRoomCard';
import type { StudyRoom,StudyRoomActions,StudyRoomPerson } from './model';

export interface StudyRoomDraft {
  subject: string; topic: string; description: string; startsAt: string; endsAt: string; capacity: number; link: string;
}
export interface CreateStudyRoomFormProps extends FormFeedbackProps { onSubmit: (draft: StudyRoomDraft) => Promise<void> }
export function CreateStudyRoomForm({ onSubmit, fieldErrors }: CreateStudyRoomFormProps) {
  const { t } = useTranslation();
  return <FormFields title={t('rooms.create')} fields={[
    {name:'subject',label:'fields.subject'}, {name:'topic',label:'fields.topic'}, {name:'description',label:'fields.description'},
    {name:'startsAt',label:'fields.start',type:'datetime-local'}, {name:'endsAt',label:'fields.end',type:'datetime-local'},
    {name:'capacity',label:'fields.capacity',type:'number'}, {name:'link',label:'fields.link',type:'url'},
  ]} submitLabel="common.submit" fieldErrors={fieldErrors} onSubmit={v => onSubmit({subject:v.subject,topic:v.topic,
    description:v.description,startsAt:v.startsAt,endsAt:v.endsAt,capacity:Number(v.capacity),link:v.link})} />;
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
