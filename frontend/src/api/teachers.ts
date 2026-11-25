import type { TeacherData } from '../types/subjects';
import { request } from './client';

export const getTeachers = () => request<TeacherData[]>('GET', '/teachers/');
