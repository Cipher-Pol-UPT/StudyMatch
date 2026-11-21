import type { CategoryData, CategoryRequest } from '../types/categories';
import { request } from './client';

export const getCategories = () => request<CategoryData[]>('GET', '/categories/');

export const registerCategory = (category: CategoryRequest) => request<CategoryData>('POST', '/categories/', category);

// its subjects and the students' levels stay linked, only the name changes
export const renameCategory = (id: number, category: CategoryRequest) => request<CategoryData>('PUT', `/categories/${id}`, category);
