import type { ChatContactData, DirectChatData } from '../types/chat';
import type { PageData } from '../types/studentDirectory';
import { request } from './client';

// who you can message, by name, institutional email or number, 20 a page
export const searchChatContacts = (query: string) => request<PageData<ChatContactData>>('GET', `/users/chat-contacts?${query}`);
// your DM with that person, or an empty one if you've never talked
export const getDirectChat = (number: number) => request<DirectChatData>('GET', `/chats/direct/${number}`);
