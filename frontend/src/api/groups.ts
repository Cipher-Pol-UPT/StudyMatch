import type { GroupPreviewData } from '../types/groups';
import { request } from './client';

const groupsOf = (editionId: number, assignmentId: number) => `/subject-editions/${editionId}/assignments/${assignmentId}/groups`;

// replacePublished: the teacher was warned that the published groups go once the new ones are confirmed
export const generateGroups = (editionId: number, assignmentId: number, replacePublished = false) =>
  request<GroupPreviewData[]>('POST', `${groupsOf(editionId, assignmentId)}${replacePublished ? '?replacePublished=true' : ''}`);
export const publishGroups = (editionId: number, assignmentId: number) =>
  request<void>('POST', `${groupsOf(editionId, assignmentId)}/publish`);
export const discardGroups = (editionId: number, assignmentId: number) =>
  request<void>('DELETE', `${groupsOf(editionId, assignmentId)}/draft`);
