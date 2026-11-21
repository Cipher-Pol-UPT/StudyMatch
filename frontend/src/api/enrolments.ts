import type { EnrolmentResultData, EnrolStudentsRequest } from '../types/enrolments';
import type { EnrolmentRecordData, SubjectEditionData } from '../types/studentDirectory';
import { download, request, upload } from './client';

export const getCurrentEditions = () => request<SubjectEditionData[]>('GET', '/subject-editions/current');

export const enrolStudents = (editionId: number, enrolment: EnrolStudentsRequest) =>
  request<EnrolmentResultData[]>('POST', `/subject-editions/${editionId}/enrolments`, enrolment);

// another class of the same edition, the grades and absences stay
export const changeClass = (editionId: number, studentNumber: number, classSection: string) =>
  request<EnrolmentRecordData>('PUT', `/subject-editions/${editionId}/enrolments/${studentNumber}/class-section`, { classSection });

export const downloadEnrolmentTemplate = () => download('/subject-editions/enrolments/import/template');

export function importEnrolments(editionId: number, classSection: string, file: File): Promise<EnrolmentResultData[]> {
  const form = new FormData();
  form.append('file', file);
  return upload<EnrolmentResultData[]>(`/subject-editions/${editionId}/enrolments/import?classSection=${encodeURIComponent(classSection)}`, form);
}
