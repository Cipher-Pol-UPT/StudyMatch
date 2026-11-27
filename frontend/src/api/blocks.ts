import type { ChatContactData } from '../types/chat';
import { request } from './client';

// the people you blocked, by name (#224). Blocking only touches DMs and the blocked person is never told (GR9)
export const getBlocked = () => request<ChatContactData[]>('GET', '/blocks/');
export const blockUser = (number: number) => request<void>('PUT', `/blocks/${number}`);
export const unblockUser = (number: number) => request<void>('DELETE', `/blocks/${number}`);
