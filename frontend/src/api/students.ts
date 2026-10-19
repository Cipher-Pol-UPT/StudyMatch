import type { StudentProfile } from '../studymatch-ui';
import { request } from './client';

export function getMyProfile(): Promise<StudentProfile> {
  return request<StudentProfile>('GET', '/students/me/profile');
}