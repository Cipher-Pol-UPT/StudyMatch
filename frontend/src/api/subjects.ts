import type { SubjectData } from '../types/studyRooms';
import { request } from './client';

export const getSubjects = () => request<SubjectData[]>('GET', '/subjects/');
