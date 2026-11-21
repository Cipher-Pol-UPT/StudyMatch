import type { StudyRoomStatus } from './enums';

export interface TutorSubjectData { subjectId: number; subjectName: string }
export interface CreateStudyRoomRequest {
  subjectId: number | null; startsAt: string | null; endsAt: string | null;
  description: string | null; capacity: number; link: string | null;
}
export interface StudyRoomData {
  id: number; subjectId: number; subjectName: string; tutorNumber: number; tutorName: string;
  startsAt: string; endsAt: string; description: string | null; capacity: number; link: string;
  status: StudyRoomStatus; participantCount: number;
}
