import type { ChatEventType, Role } from './enums';

// what the backend pushes over the websocket, the payload depends on the type
export interface ChatEvent<T = unknown> {
  type: ChatEventType;
  payload: T;
}

// someone you can message, a row of the New message search
export interface ChatContactData { number: number; name: string; role: Role }

// your DM with someone: chatId is null until the first message creates it, or while you've blocked them
export interface DirectChatData { chatId: number | null; contact: ChatContactData; blocked: boolean }

// the message a reply quotes
export interface MessageQuoteData { id: number; author: ChatContactData; text: string }

// a message as the chat shows it; editedAt is null until it's edited.
// a reply with a null replyTo shows that the original was deleted
export interface MessageData {
  id: number;
  chatId: number;
  author: ChatContactData;
  text: string;
  sentAt: string;
  editedAt: string | null;
  reply: boolean;
  replyTo: MessageQuoteData | null;
  // its files (#210); text is empty when it only has these
  attachments?: AttachmentData[];
}

// a message's file without what's in it, size in bytes
export interface AttachmentData { id: number; name: string; contentType: string; size: number }

// one person's reaction to a message, replaces the one they had; a null emoji means they took it off
export interface ReactionData { chatId: number; messageId: number; user: ChatContactData; emoji: string | null }

// the most characters a message can have (GR10)
export const MESSAGE_MAX_LENGTH = 2000;

// a page of an open chat, oldest first; hasMore is false once the first message is in
export interface MessagePageData { messages: MessageData[]; hasMore: boolean }

// what MESSAGE_DELETED carries: the message is gone, only its id is left
export interface MessageDeletedData { chatId: number; messageId: number }

// a row of the chat list (#201): contact is the other person in a DM, null in a group chat. picture says if a group chat
// has one, and pictureUpdatedAt goes in its url so a new one isn't cached. lastMessage is null before anyone writes
export interface ChatSummaryData {
  chatId: number;
  name: string;
  contact: ChatContactData | null;
  picture: boolean;
  pictureUpdatedAt: string | null;
  lastMessage: MessageData | null;
  unread: number;
}

// what CHAT_EDITED carries: a group chat's new name or picture
export interface ChatEditedData { chatId: number; name: string; picture: boolean; pictureUpdatedAt: string | null }
