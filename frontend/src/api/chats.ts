import type { ChatContactData, ChatSummaryData, DirectChatData, MessageData, MessagePageData, ReactionData } from '../types/chat';
import type { PageData } from '../types/studentDirectory';
import { request } from './client';

// who you can message, by name, institutional email or number, 20 a page
export const searchChatContacts = (query: string) => request<PageData<ChatContactData>>('GET', `/users/chat-contacts?${query}`);
// your DM with that person, or an empty one if you've never talked
export const getDirectChat = (number: number) => request<DirectChatData>('GET', `/chats/direct/${number}`);
// send a DM: it goes into the DM you have with them, and the first one creates it
// with replyToId it answers that message (#218)
export const sendDirectMessage = (number: number, text: string, replyToId?: number) =>
  request<MessageData>('POST', `/chats/direct/${number}/messages`, replyToId === undefined ? { text } : { text, replyToId });
// a chat's 50 newest messages, the 50 before a message when scrolling up, or the 50 after one when scrolling down
// from an old message you jumped to
export const getChatMessages = (chatId: number, from?: { before: number } | { after: number }) =>
  request<MessagePageData>('GET', `/chats/${chatId}/messages${from === undefined ? '' : `?${new URLSearchParams(Object.entries(from).map(([key, id]) => [key, String(id)]))}`}`);
// a message with up to 25 on each side, to jump to it
export const getMessagesAround = (chatId: number, messageId: number) =>
  request<MessagePageData>('GET', `/chats/${chatId}/messages/${messageId}/around`);
// marks the chat as read up to now, which clears its unread count
export const markChatRead = (chatId: number) => request<void>('PUT', `/chats/${chatId}/read`);
// change your own message's text, it comes back with editedAt
export const editMessage = (chatId: number, messageId: number, text: string) =>
  request<MessageData>('PUT', `/chats/${chatId}/messages/${messageId}`, { text });
// react with one emoji, replacing yours (#216)
export const reactToMessage = (chatId: number, messageId: number, emoji: string) =>
  request<ReactionData>('PUT', `/chats/${chatId}/messages/${messageId}/reaction`, { emoji });
// take your reaction off
export const removeReaction = (chatId: number, messageId: number) => request<void>('DELETE', `/chats/${chatId}/messages/${messageId}/reaction`);
// delete your own message for everyone
export const deleteMessage = (chatId: number, messageId: number) => request<void>('DELETE', `/chats/${chatId}/messages/${messageId}`);

// your chats, the latest message first, with what you haven't read (#201)
export const getChats = () => request<ChatSummaryData[]>('GET', '/chats/');

// how many messages you haven't read across every chat, for the side menu
export const getUnreadCount = () => request<number>('GET', '/chats/unread-count');
