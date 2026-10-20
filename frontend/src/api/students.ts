import type { ImportPreviewRow, ImportResultRow, RegisteredStudent, StudentRegistration } from '../types/registration';
import type { StudentProfileData, UpdateProfileRequest } from '../types/studentProfile';
import { download, request, upload } from './client';

export function getMyProfile(): Promise<StudentProfileData> {
  return request<StudentProfileData>('GET', '/students/me/profile');
}

export function updateMyProfile(changes: UpdateProfileRequest): Promise<StudentProfileData> {
  return request<StudentProfileData>('PUT', '/students/me/profile', changes);
}

export function registerStudent(student: StudentRegistration): Promise<RegisteredStudent> {
  return request<RegisteredStudent>('POST', '/students/', student);
}

export function resendCredentials(studentNumber: number, personalEmail?: string): Promise<void> {
  return request<void>('POST', `/students/${studentNumber}/resend-credentials`, { personalEmail: personalEmail || null });
}

export function downloadImportTemplate(): Promise<Blob> {
  return download('/students/import/template');
}

export function previewImport(file: File): Promise<ImportPreviewRow[]> {
  const form = new FormData();
  form.append('file', file);
  return upload<ImportPreviewRow[]>('/students/import/preview', form);
}

export function importStudents(rows: ImportPreviewRow[]): Promise<ImportResultRow[]> {
  return request<ImportResultRow[]>('POST', '/students/import', { rows: rows.map(({ row, student }) => ({ row, student })) });
}
