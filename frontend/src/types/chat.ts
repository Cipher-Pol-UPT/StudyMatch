import type { ChatEventType, Role } from './enums';

// what the backend pushes over the websocket, the payload depends on the type
export interface ChatEvent<T = unknown> {
  type: ChatEventType;
  payload: T;
}

// someone you can message, a row of the New message search
export interface ChatContactData { number: number; name: string; role: Role }

// your DM with someone: chatId is null until the first message creates it
export interface DirectChatData { chatId: number | null; contact: ChatContactData }
