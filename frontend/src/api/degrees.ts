import type { DegreeData } from '../types/registration';
import { request } from './client';

export function listDegrees(): Promise<DegreeData[]> {
  return request<DegreeData[]>('GET', '/degrees/');
}
