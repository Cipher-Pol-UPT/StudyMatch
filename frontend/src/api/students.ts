import type { StudentProfileData, UpdateProfileRequest } from '../types/studentProfile';
import { request } from './client';

export function getMyProfile(): Promise<StudentProfileData> {
  return request<StudentProfileData>('GET', '/students/me/profile');
}

export function updateMyProfile(changes: UpdateProfileRequest): Promise<StudentProfileData> {
  return request<StudentProfileData>('PUT', '/students/me/profile', changes);
}
