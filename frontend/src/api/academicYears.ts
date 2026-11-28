import type { AcademicYearData, DayOffData, DayOffRequest, SemesterDatesData } from '../types/academicYears';
import { request } from './client';

// every academic year, the current one first
export const getAcademicYears = () => request<AcademicYearData[]>('GET', '/academic-years/');

// the year's days without classes, the earliest first (#336)
export const getDaysOff = (startYear: number) => request<DayOffData[]>('GET', `/academic-years/${startYear}/days-off`);
export const addDayOff = (startYear: number, day: DayOffRequest) =>
  request<DayOffData>('POST', `/academic-years/${startYear}/days-off`, day);
export const removeDayOff = (startYear: number, id: number) => request<void>('DELETE', `/academic-years/${startYear}/days-off/${id}`);

// the current year's semester dates (#334); answers the year with its new dates
export const editSemesters = (startYear: number, semesters: SemesterDatesData) =>
  request<AcademicYearData>('PUT', `/academic-years/${startYear}/semesters`, semesters);
