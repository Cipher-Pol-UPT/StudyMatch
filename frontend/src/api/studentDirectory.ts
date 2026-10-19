import type { DegreeData, EditionStudentData, PageData, StudentRecordData, StudentSummaryData, SubjectEditionData } from '../types/studentDirectory';
import type { StudentProfileData } from '../types/studentProfile';
import { request } from './client';

export const getDegrees = () => request<DegreeData[]>('GET', '/degrees/');
export const getMySubjectEditions = () => request<SubjectEditionData[]>('GET', '/subject-editions/');
export const getStudents = (query: string) => request<PageData<StudentSummaryData>>('GET', `/students/?${query}`);
export const getStudentRecord = (number: number) => request<StudentRecordData>('GET', `/students/${number}`);
export const getStudentProfile = (number: number) => request<StudentProfileData>('GET', `/students/${number}/profile`);
export const getEditionStudents = (editionId: number, query = '') => request<EditionStudentData[]>('GET', `/subject-editions/${editionId}/students${query ? `?${query}` : ''}`);
