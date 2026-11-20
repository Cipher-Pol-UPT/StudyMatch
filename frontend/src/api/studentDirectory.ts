import type { EditionStudentData, PageData, StatuteRequest, StudentRecordData, StudentSummaryData, SubjectEditionData } from '../types/studentDirectory';
import type { StudentProfileData } from '../types/studentProfile';
import { request } from './client';

export const getMySubjectEditions = () => request<SubjectEditionData[]>('GET', '/subject-editions/');
export const getStudents = (query: string) => request<PageData<StudentSummaryData>>('GET', `/students/?${query}`);
export const getStudentRecord = (number: number) => request<StudentRecordData>('GET', `/students/${number}`);
export const setStatute = (number: number, statute: StatuteRequest) => request<StudentRecordData>('PUT', `/students/${number}/statute`, statute);
export const removeStatute = (number: number) => request<void>('DELETE', `/students/${number}/statute`);
export const getStudentProfile = (number: number) => request<StudentProfileData>('GET', `/students/${number}/profile`);
export const getEditionStudents = (editionId: number, query = '') => request<EditionStudentData[]>('GET', `/subject-editions/${editionId}/students${query ? `?${query}` : ''}`);
