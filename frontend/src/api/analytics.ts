import type { AnalyticsData } from '../types/analytics';
import { request } from './client';

// query is academicYear, subjectEditionId, classSection and groupId, all optional
export const getAnalytics = (query = '') => request<AnalyticsData>('GET', `/analytics/${query ? `?${query}` : ''}`);
