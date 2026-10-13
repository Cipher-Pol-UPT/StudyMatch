import type { ErrorCode } from '../../../api/errors';
import type { StudyRoomStatus } from '../../../types/enums';
export type { StudyRoomStatus } from '../../../types/enums';
export interface StudyRoomPerson { id: string; name: string; avatarUrl?: string }
export interface StudyRoom {
  id: string; subject: string; topic: string; description?: string;
  start: Date | string; end: Date | string; tutor: StudyRoomPerson;
  capacity: number; participants: StudyRoomPerson[]; meetUrl?: string; status: StudyRoomStatus;
}
export interface StudyRoomActions {
  canJoin?: boolean; canLeave?: boolean; canEnter?: boolean; canCancel?: boolean;
  canConfirmAttendance?: boolean; joinBlockedCode?: ErrorCode;
}
export const toDate = (value: Date | string) => typeof value === 'string' ? new Date(value) : value;
export function formatRoomDate(value: Date | string, locale: string) {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(toDate(value));
}
