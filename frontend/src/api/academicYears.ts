import type { AcademicYearPreviewData } from '../types/subjects';
import { request } from './client';

export const getNextAcademicYear = () => request<AcademicYearPreviewData>('GET', '/academic-years/next');
