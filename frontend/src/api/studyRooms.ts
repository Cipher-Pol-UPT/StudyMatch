import type { CreateStudyRoomRequest, StudyRoomData, TutorSubjectData } from '../types/studyRooms';
import { request } from './client';

export const getMyTutorSubjects = () => request<TutorSubjectData[]>('GET', '/tutors/me/subjects');

export const createStudyRoom = (room: CreateStudyRoomRequest) => request<StudyRoomData>('POST', '/study-rooms/', room);
