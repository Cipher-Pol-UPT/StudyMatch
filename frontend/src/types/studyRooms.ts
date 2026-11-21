import type { StudyRoomStatus } from './enums';

export interface TutorSubjectData { subjectId: number; subjectName: string }
export interface SubjectData { id: number; name: string }
export interface CreateStudyRoomRequest {
  subjectId: number | null; startsAt: string | null; endsAt: string | null;
  description: string | null; capacity: number; link: string | null;
}
// the View StudyRoom (#43) calendar row: the link is null unless you host the room or already joined
export interface StudyRoomData {
  id: number; subjectId: number; subjectName: string; tutorNumber: number; tutorName: string;
  startsAt: string; endsAt: string; description: string | null; capacity: number; link: string | null;
  status: StudyRoomStatus; participantCount: number; availablePlaces: number;
}
// the View StudyRoom (#43) detail: the calendar row plus the tutor's rating and what you can do
export interface StudyRoomDetailData extends StudyRoomData {
  tutorAverageRating: number | null; tutorRatingCount: number;
  hosted: boolean; joined: boolean; canJoin: boolean;
}
