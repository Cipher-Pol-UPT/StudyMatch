import type { GradeResultData, GradeSheetData } from '../types/grades';
import { request } from './client';

// also the list of a subject's assignments and their weights
export const getGradeSheet = (editionId: number) => request<GradeSheetData>('GET', `/subject-editions/${editionId}/grades`);

// grading again replaces the grade, and the student's final grade is worked out again
export const gradeStudent = (editionId: number, assignmentId: number, studentNumber: number, value: number) =>
  request<GradeResultData>('PUT', `/subject-editions/${editionId}/assignments/${assignmentId}/grades/${studentNumber}`, { value });
