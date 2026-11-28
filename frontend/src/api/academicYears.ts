import type { AcademicYearData, DayOffData, DayOffRequest } from '../types/academicYears';
import { request } from './client';

// every academic year, the current one first
export const getAcademicYears = () => request<AcademicYearData[]>('GET', '/academic-years/');

// the year's days without classes, the earliest first (#336)
export const getDaysOff = (startYear: number) => request<DayOffData[]>('GET', `/academic-years/${startYear}/days-off`);
export const addDayOff = (startYear: number, day: DayOffRequest) =>
  request<DayOffData>('POST', `/academic-years/${startYear}/days-off`, day);
export const removeDayOff = (startYear: number, id: number) => request<void>('DELETE', `/academic-years/${startYear}/days-off/${id}`);
