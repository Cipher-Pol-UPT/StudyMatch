import { useCallback, useSyncExternalStore } from 'react';

// a message of this conversation: 'sending' until the backend confirms it, 'failed' to retry or discard
export interface ThreadMessage {
  key: string;
  text: string;
  sentAt: string;
  status?: 'sending' | 'failed';
}

// what's typed and sent in one conversation
export interface Conversation {
  draft: string;
  messages: ThreadMessage[];
  // why the last send failed, shown under the message box
  error?: unknown;
}

const EMPTY: Conversation = { draft: '', messages: [] };

// kept outside the page while the app is open, so switching chats, or going back to the list on a phone, loses no draft
const conversations = new Map<number, Conversation>();
const listeners = new Set<() => void>();

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

// the conversation with that person, and a way to change it that still works after the page is gone (a send finishing late)
export function useConversation(number: number) {
  const conversation = useSyncExternalStore(subscribe, () => conversations.get(number) ?? EMPTY);
  const update = useCallback((change: (current: Conversation) => Conversation) => {
    conversations.set(number, change(conversations.get(number) ?? EMPTY));
    listeners.forEach(listener => listener());
  }, [number]);
  return [conversation, update] as const;
}

// on logout, so the next person on this browser doesn't see them
export function forgetConversations() {
  conversations.clear();
  listeners.forEach(listener => listener());
}

let lastKey = 0;
// a key for a message until the backend gives it an id
export const newMessageKey = () => `local-${++lastKey}`;
