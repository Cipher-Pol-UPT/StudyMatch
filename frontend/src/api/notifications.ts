import type { NotificationData } from '../types/notifications';
import type { PageData } from '../types/studentDirectory';
import { request } from './client';

// yours, newest first, 20 a page
export const getNotifications = (page = 0) => request<PageData<NotificationData>>('GET', `/notifications/?page=${page}`);
// how many you haven't read, for the bell
export const getUnreadNotifications = () => request<number>('GET', '/notifications/unread-count');
export const markNotificationRead = (id: number) => request<void>('PUT', `/notifications/${id}/read`);
export const markAllNotificationsRead = () => request<void>('PUT', '/notifications/read');
