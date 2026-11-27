import { useCallback, useSyncExternalStore } from 'react';
import type { MessageQuoteData } from '../../types/chat';
import type { Sticker } from '../../types/enums';

// a message of this conversation: 'sending' until the backend confirms it, 'failed' to retry or discard
export interface ThreadMessage {
  key: string;
  text: string;
  sentAt: string;
  status?: 'sending' | 'failed';
  // the message it answers (#219)
  replyTo?: MessageQuoteData;
  // its files while they go up, and how much of them has (#211)
  files?: File[];
  progress?: number;
  // a sticker instead of the text, which is empty then (#215)
  sticker?: Sticker;
}

// what's typed and sent in one conversation
export interface Conversation {
  draft: string;
  messages: ThreadMessage[];
  // why the last send failed, shown under the message box
  error?: unknown;
  // the message the next one answers, shown above the message box
  replyTo?: MessageQuoteData;
  // added to the next one, shown above the message box (#211)
  files?: File[];
}

const EMPTY: Conversation = { draft: '', messages: [] };

// kept outside the page while the app is open, so switching chats, or going back to the list on a phone, loses no draft
const conversations = new Map<string, Conversation>();
const listeners = new Set<() => void>();
// goes up on every logout, so a send that finishes later can't put a forgotten conversation back
let generation = 0;

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

// the conversation in a DM ("direct:<number>") or a group chat ("chat:<id>"), and a way to change it that still works after the page is gone (a send finishing late)
export function useConversation(key: string) {
  const conversation = useSyncExternalStore(subscribe, () => conversations.get(key) ?? EMPTY);
  const session = generation;
  const update = useCallback((change: (current: Conversation) => Conversation) => {
    if (session !== generation) return;
    conversations.set(key, change(conversations.get(key) ?? EMPTY));
    listeners.forEach(listener => listener());
  }, [key, session]);
  return [conversation, update] as const;
}

// on logout, so the next person on this browser doesn't see them
export function forgetConversations() {
  generation++;
  conversations.clear();
  listeners.forEach(listener => listener());
}

let lastKey = 0;
// a key for a message until the backend gives it an id
export const newMessageKey = () => `local-${++lastKey}`;
