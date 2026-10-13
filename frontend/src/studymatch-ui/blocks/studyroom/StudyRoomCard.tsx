import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { StatusPill } from '../../components/badge/Badge';
import { Button,type ButtonProps } from '../../components/button/Button';
import { Card } from '../../components/card/Card';
import { formatRoomDate,type StudyRoom,type StudyRoomActions } from './model';
import './studyroom.css';
export interface MeetButtonProps extends Omit<ButtonProps, 'onClick'> { href?: string; onEnter?: () => void; children?: ReactNode }
export function MeetButton({ href, onEnter, children, ...rest }: MeetButtonProps) {
  const { t } = useTranslation();
  return href ? <a href={href} target="_blank" rel="noopener noreferrer" onClick={onEnter} className="sm-btn sm-btn--primary">{children ?? t('rooms.enter')}</a>
    : <Button {...rest} onClick={onEnter}>{children ?? t('rooms.enter')}</Button>;
}
export interface StudyRoomStatusPillProps { room: StudyRoom }
export function StudyRoomStatusPill({ room }: StudyRoomStatusPillProps) {
  const { t } = useTranslation(); return <StatusPill>{t(`studyRoomStatuses.${room.status}`)}</StatusPill>;
}
export interface StudyRoomCardProps extends StudyRoomActions {
  room: StudyRoom; onJoin?: (room: StudyRoom) => void; onLeave?: (room: StudyRoom) => void;
  onEnter?: (room: StudyRoom) => void; onSelect?: (room: StudyRoom) => void;
}
export function StudyRoomCard({ room, canJoin, canLeave, canEnter, joinBlockedCode, onJoin, onLeave, onEnter, onSelect }: StudyRoomCardProps) {
  const { t, i18n } = useTranslation();
  return <Card title={room.topic} description={room.subject}>
    <StudyRoomStatusPill room={room} /><p>{formatRoomDate(room.start, i18n.language)} — {formatRoomDate(room.end, i18n.language)}</p>
    <p>{room.tutor.name}</p><p>{room.description}</p>
    {joinBlockedCode && <p role="status">{t(`errors.${joinBlockedCode}`)}</p>}
    {canJoin && onJoin && <Button disabled={!!joinBlockedCode} onClick={() => onJoin(room)}>{t('rooms.join')}</Button>}
    {canLeave && onLeave && <Button onClick={() => onLeave(room)}>{t('rooms.leave')}</Button>}
    {canEnter && <MeetButton href={room.meetUrl} onEnter={() => onEnter?.(room)} />}
    {onSelect && <Button variant="secondary" onClick={() => onSelect(room)}>{t('rooms.details')}</Button>}
  </Card>;
}
