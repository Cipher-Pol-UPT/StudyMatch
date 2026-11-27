import type { ChatContactData, ChatSummaryData, DirectChatData, MessageData, MessagePageData, MessageSearchData, ReactionData } from '../types/chat';
import type { PageData } from '../types/studentDirectory';
import { request, uploadWithProgress } from './client';

// who you can message, by name, institutional email or number, 20 a page
export const searchChatContacts = (query: string) => request<PageData<ChatContactData>>('GET', `/users/chat-contacts?${query}`);
// your DM with that person, or an empty one if you've never talked
export const getDirectChat = (number: number) => request<DirectChatData>('GET', `/chats/direct/${number}`);
// send a DM: it goes into the DM you have with them, and the first one creates it
// with replyToId it answers that message (#218)
export const sendDirectMessage = (number: number, text: string, replyToId?: number) =>
  request<MessageData>('POST', `/chats/direct/${number}/messages`, replyToId === undefined ? { text } : { text, replyToId });
// send to a chat you're in by its id, a group chat (#227)
export const sendMessage = (chatId: number, text: string, replyToId?: number) =>
  request<MessageData>('POST', `/chats/${chatId}/messages`, replyToId === undefined ? { text } : { text, replyToId });
// a message with files (#211), to a chat by its id or to a DM by the person's number; the text can be empty
export function sendFiles(to: { chatId: number } | { number: number }, text: string, files: File[], replyToId: number | undefined,
  onProgress: (percent: number) => void) {
  const form = new FormData();
  files.forEach(file => form.append('files', file, file.name));
  if (text) form.append('text', text);
  if (replyToId !== undefined) form.append('replyToId', String(replyToId));
  const path = 'chatId' in to ? `/chats/${to.chatId}/messages` : `/chats/direct/${to.number}/messages`;
  return uploadWithProgress<MessageData>(path, form, onProgress);
}
// the address of a message's file, for <img src> and downloading it
export const attachmentUrl = (chatId: number, messageId: number, attachmentId: number) =>
  `/api/chats/${chatId}/messages/${messageId}/attachments/${attachmentId}`;
// a chat's 50 newest messages, the 50 before a message when scrolling up, or the 50 after one when scrolling down
// from an old message you jumped to
export const getChatMessages = (chatId: number, from?: { before: number } | { after: number }) =>
  request<MessagePageData>('GET', `/chats/${chatId}/messages${from === undefined ? '' : `?${new URLSearchParams(Object.entries(from).map(([key, id]) => [key, String(id)]))}`}`);
// the chat's messages with that text, newest first; before is the oldest result's id, for the next 25
export const searchChat = (chatId: number, text: string, before?: number) =>
  request<MessageSearchData>('GET', `/chats/${chatId}/messages/search?${new URLSearchParams(before === undefined ? { text } : { text, before: String(before) })}`);
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

// a group chat you own with 2 to 19 others; picture is an optional data URL like the profile photo (#227)
export const createGroupChat = (name: string, memberNumbers: number[], picture: string | null) =>
  request<ChatSummaryData>('POST', '/chats/groups', { name, memberNumbers, picture });
// add people to a group chat you're in, anyone already in is skipped; the members come back (#229)
export const addChatMembers = (chatId: number, memberNumbers: number[]) =>
  request<ChatContactData[]>('POST', `/chats/${chatId}/members`, { memberNumbers });
// who's in a chat, the owner first then in the order they joined
export const getChatMembers = (chatId: number) => request<ChatContactData[]>('GET', `/chats/${chatId}/members`);
// the numbers of the people you share a chat with who have the app open, for the first load; the pushes keep it up to date (#223)
export const getOnlineContacts = () => request<number[]>('GET', '/chats/online');

// how many messages you haven't read across every chat, for the side menu
export const getUnreadCount = () => request<number>('GET', '/chats/unread-count');
