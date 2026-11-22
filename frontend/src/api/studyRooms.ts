import type { CreateStudyRoomRequest, StudyRoomData, StudyRoomDetailData, TutorSubjectData } from '../types/studyRooms';
import { request } from './client';

export const getMyTutorSubjects = () => request<TutorSubjectData[]>('GET', '/tutors/me/subjects');

export const createStudyRoom = (room: CreateStudyRoomRequest) => request<StudyRoomData>('POST', '/study-rooms/', room);

export const getStudyRooms = () => request<StudyRoomData[]>('GET', '/study-rooms/');

export const getStudyRoom = (id: number) => request<StudyRoomDetailData>('GET', `/study-rooms/${id}`);

export const joinStudyRoom = (id: number) => request<StudyRoomData>('POST', `/study-rooms/${id}/join`);

export const leaveStudyRoom = (id: number) => request<StudyRoomData>('POST', `/study-rooms/${id}/leave`);
