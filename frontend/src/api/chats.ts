import type { ChatContactData, DirectChatData, MessageData, MessagePageData } from '../types/chat';
import type { PageData } from '../types/studentDirectory';
import { request } from './client';

// who you can message, by name, institutional email or number, 20 a page
export const searchChatContacts = (query: string) => request<PageData<ChatContactData>>('GET', `/users/chat-contacts?${query}`);
// your DM with that person, or an empty one if you've never talked
export const getDirectChat = (number: number) => request<DirectChatData>('GET', `/chats/direct/${number}`);
// send a DM: it goes into the DM you have with them, and the first one creates it
export const sendDirectMessage = (number: number, text: string) => request<MessageData>('POST', `/chats/direct/${number}/messages`, { text });
// a chat's 50 newest messages, or the 50 before a message when scrolling up
export const getChatMessages = (chatId: number, before?: number) =>
  request<MessagePageData>('GET', `/chats/${chatId}/messages${before === undefined ? '' : `?before=${before}`}`);
// marks the chat as read up to now, which clears its unread count
export const markChatRead = (chatId: number) => request<void>('PUT', `/chats/${chatId}/read`);
// change your own message's text, it comes back with editedAt
export const editMessage = (chatId: number, messageId: number, text: string) =>
  request<MessageData>('PUT', `/chats/${chatId}/messages/${messageId}`, { text });
// delete your own message for everyone
export const deleteMessage = (chatId: number, messageId: number) => request<void>('DELETE', `/chats/${chatId}/messages/${messageId}`);
