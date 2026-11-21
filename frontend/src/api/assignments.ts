import type { AssignmentData, AssignmentRequest } from '../types/assignments';
import { request } from './client';

export const createAssignment = (editionId: number, assignment: AssignmentRequest) =>
  request<AssignmentData>('POST', `/subject-editions/${editionId}/assignments`, assignment);
