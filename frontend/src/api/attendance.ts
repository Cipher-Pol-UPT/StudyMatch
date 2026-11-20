import type { AttendanceRequest, AttendanceSheetData } from '../types/attendance';
import { request } from './client';

export const getAttendanceSheet = (editionId: number, classSection: string, startsAt: string) =>
  request<AttendanceSheetData>('GET', `/subject-editions/${editionId}/attendance?${new URLSearchParams({ classSection, startsAt }).toString()}`);

// the same call registers a lesson and corrects it
export const registerAttendance = (editionId: number, attendance: AttendanceRequest) =>
  request<AttendanceSheetData>('PUT', `/subject-editions/${editionId}/attendance`, attendance);
