import { useEffect, useRef, useSyncExternalStore } from 'react';
import { isChatSocketConnected, onChatSocketConnection, subscribeToChatEvents } from '../api/chatSocket';
import type { ChatEvent } from '../types/chat';

// a chat's messages being sent, edited or deleted, while the component is on screen; nothing while there's no chat yet
export function useChatEvents(chatId: number | null, onEvent: (event: ChatEvent) => void) {
  useSocketDestination(chatId === null ? null : `/topic/chats/${chatId}`, onEvent);
  // everything in a group chat, and DM messages only you can see, like one to someone who blocked you
  useSocketDestination(chatId === null ? null : `/user/queue/chats/${chatId}`, onEvent);
}

// the user's chat list changing (a new chat, one they left...); off for the roles that have no chats
export function useChatListEvents(onEvent: (event: ChatEvent) => void, enabled = true) {
  useSocketDestination(enabled ? '/user/queue/chats' : null, onEvent);
}

// the latest handler is kept in a ref, so a new function each render doesn't resubscribe
function useSocketDestination(destination: string | null, onEvent: (event: ChatEvent) => void) {
  const handler = useRef(onEvent);
  useEffect(() => {
    handler.current = onEvent;
  });
  useEffect(() => destination === null ? undefined : subscribeToChatEvents(destination, event => handler.current(event)), [destination]);
}

// whether the live connection is up right now (#223)
export function useChatSocketConnected() {
  return useSyncExternalStore(onChatSocketConnection, isChatSocketConnected);
}
