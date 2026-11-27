import type { SubjectEditionData } from '../types/studentDirectory';
import type { SubjectData, SubjectRequest } from '../types/subjects';
import { request } from './client';

export const getSubjects = () => request<SubjectData[]>('GET', '/subjects/');

export const getSubject = (id: number) => request<SubjectData>('GET', `/subjects/${id}`);

export const registerSubject = (subject: SubjectRequest) => request<SubjectEditionData>('POST', '/subjects/', subject);

// a subject's code is already taken, so Staff can add this year's edition of the existing subject instead
export const registerEdition = (subjectId: number, edition: SubjectRequest['edition']) =>
  request<SubjectEditionData>('POST', `/subjects/${subjectId}/editions`, edition);
