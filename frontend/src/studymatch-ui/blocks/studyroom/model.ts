import type { ErrorCode } from '../../../api/errors';
import type { StudyRoomStatus } from '../../../types/enums';
export type { StudyRoomStatus } from '../../../types/enums';
export interface StudyRoomPerson { id: string; name: string; avatarUrl?: string }
// mirrors the backend's StudyRoomDTO (View StudyRoom #43): the link is null unless you host or joined
export interface StudyRoom {
  id: number; subjectId: number; subjectName: string; tutorNumber: number; tutorName: string;
  startsAt: string; endsAt: string; description: string | null; capacity: number; link: string | null;
  status: StudyRoomStatus; participantCount: number; availablePlaces: number;
}
export interface StudyRoomActions {
  canJoin?: boolean; canLeave?: boolean; canEnter?: boolean; canCancel?: boolean;
  canConfirmAttendance?: boolean; joinBlockedCode?: ErrorCode;
}
export const CAPACITY_RANGE = { min: 2, max: 20 } as const;
export function formatRoomDate(value: string, locale: string) {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
