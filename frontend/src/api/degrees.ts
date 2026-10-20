import type { DegreeData } from '../types/studentDirectory';
import { request } from './client';

export const getDegrees = () => request<DegreeData[]>('GET', '/degrees/');
