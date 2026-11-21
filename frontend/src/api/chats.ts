import type { ChatContactData, DirectChatData, MessageData } from '../types/chat';
import type { PageData } from '../types/studentDirectory';
import { request } from './client';

// who you can message, by name, institutional email or number, 20 a page
export const searchChatContacts = (query: string) => request<PageData<ChatContactData>>('GET', `/users/chat-contacts?${query}`);
// your DM with that person, or an empty one if you've never talked
export const getDirectChat = (number: number) => request<DirectChatData>('GET', `/chats/direct/${number}`);
// send a DM: it goes into the DM you have with them, and the first one creates it
export const sendDirectMessage = (number: number, text: string) => request<MessageData>('POST', `/chats/direct/${number}/messages`, { text });
