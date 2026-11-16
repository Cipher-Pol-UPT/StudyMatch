import type { CategoryData, CategoryRequest } from '../types/categories';
import { request } from './client';

export const getCategories = () => request<CategoryData[]>('GET', '/categories/');

export const registerCategory = (category: CategoryRequest) => request<CategoryData>('POST', '/categories/', category);
