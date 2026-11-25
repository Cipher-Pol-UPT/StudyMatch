import type { AcademicYearData } from '../types/subjects';
import { request } from './client';

export const getAcademicYears = () => request<AcademicYearData[]>('GET', '/academic-years/');
