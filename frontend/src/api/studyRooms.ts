import type { CreateStudyRoomRequest, ParticipantData, StudyRoomData, StudyRoomDetailData, TutorApplicationData, TutorEligibleSubjectData, TutorSubjectData, UpdateStudyRoomRequest } from '../types/studyRooms';
import { request } from './client';

export const getMyTutorSubjects = () => request<TutorSubjectData[]>('GET', '/tutors/me/subjects');

// Apply for tutoring (#40)
export const getApplicableSubjects = () => request<TutorEligibleSubjectData[]>('GET', '/tutors/me/applicable-subjects');

export const applyForTutoring = (subjectId: number, motivation: string | null) =>
  request<TutorApplicationData>('POST', '/tutor-applications/', { subjectId, motivation });

export const createStudyRoom = (room: CreateStudyRoomRequest) => request<StudyRoomData>('POST', '/study-rooms/', room);

export const getStudyRooms = () => request<StudyRoomData[]>('GET', '/study-rooms/');

export const getStudyRoom = (id: number) => request<StudyRoomDetailData>('GET', `/study-rooms/${id}`);

export const joinStudyRoom = (id: number) => request<StudyRoomData>('POST', `/study-rooms/${id}/join`);

export const leaveStudyRoom = (id: number) => request<StudyRoomData>('POST', `/study-rooms/${id}/leave`);

export const updateStudyRoom = (id: number, room: UpdateStudyRoomRequest) => request<StudyRoomData>('PUT', `/study-rooms/${id}`, room);

// the reason goes to the participants, null for none
export const cancelStudyRoom = (id: number, reason: string | null) => request<StudyRoomData>('POST', `/study-rooms/${id}/cancel`, { reason });

export const getStudyRoomParticipants = (id: number) => request<ParticipantData[]>('GET', `/study-rooms/${id}/participants`);

// the rest count as absent, and the room becomes Completed
export const confirmAttendance = (id: number, attendeeNumbers: number[]) =>
  request<StudyRoomData>('POST', `/study-rooms/${id}/attendance`, { attendeeNumbers });
