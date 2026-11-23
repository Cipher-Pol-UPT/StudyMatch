import { useEffect, useRef } from 'react';
import { subscribeToChatEvents } from '../api/chatSocket';
import type { ChatEvent } from '../types/chat';

// a chat's messages being sent, edited or deleted, while the component is on screen
export function useChatEvents(chatId: number, onEvent: (event: ChatEvent) => void) {
  useSocketDestination(`/topic/chats/${chatId}`, onEvent);
  // everything in a group chat, and DM messages only you can see, like one to someone who blocked you
  useSocketDestination(`/user/queue/chats/${chatId}`, onEvent);
}

// the user's chat list changing (a new chat, one they left...)
export function useChatListEvents(onEvent: (event: ChatEvent) => void) {
  useSocketDestination('/user/queue/chats', onEvent);
}

// the latest handler is kept in a ref, so a new function each render doesn't resubscribe
function useSocketDestination(destination: string, onEvent: (event: ChatEvent) => void) {
  const handler = useRef(onEvent);
  useEffect(() => {
    handler.current = onEvent;
  });
  useEffect(() => subscribeToChatEvents(destination, event => handler.current(event)), [destination]);
}
