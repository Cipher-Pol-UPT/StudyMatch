import type { TFunction } from 'i18next';
import { useCallback, useEffect, useRef, useState } from 'react';
import { sendTyping } from '../../api/chatSocket';
import type { ChatContactData, ChatEvent, MessageData } from '../../types/chat';

// there's no "stopped typing": someone is typing until 5 seconds pass without another TYPING, or their message comes
export const TYPING_SHOWN_MS = 5000;
// how often your own typing is sent, so the others' 5 seconds never run out while you keep going
export const TYPING_SENT_EVERY_MS = 3000;

interface TypingData { chatId: number; user: ChatContactData }

// who's typing in the open chat, from its TYPING events, and a way to say you are (#223)
export function useTyping(chatId: number | null, isMine: (person: ChatContactData) => boolean) {
  const [typing, setTyping] = useState<ChatContactData[]>([]);
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());
  const lastSent = useRef(0);

  const stop = useCallback((number: number) => {
    clearTimeout(timers.current.get(number));
    timers.current.delete(number);
    setTyping(current => current.some(person => person.number === number) ? current.filter(person => person.number !== number) : current);
  }, []);

  // a new chat starts with nobody typing
  useEffect(() => {
    const pending = timers.current;
    return () => {
      pending.forEach(clearTimeout);
      pending.clear();
      setTyping([]);
    };
  }, [chatId]);

  // gives back true when it was about typing, so the chat can stop there
  const onEvent = (event: ChatEvent) => {
    if (event.type === 'MESSAGE_CREATED') {
      stop((event.payload as MessageData).author.number);
      return false;
    }
    if (event.type !== 'TYPING') return false;
    const { user } = event.payload as TypingData;
    // your other tab
    if (isMine(user)) return true;
    clearTimeout(timers.current.get(user.number));
    timers.current.set(user.number, setTimeout(() => stop(user.number), TYPING_SHOWN_MS));
    setTyping(current => current.some(person => person.number === user.number) ? current : [...current, user]);
    return true;
  };

  const typed = () => {
    if (chatId === null || Date.now() - lastSent.current < TYPING_SENT_EVERY_MS) return;
    lastSent.current = Date.now();
    sendTyping(chatId);
  };
  // sending a message ends it, the next key starts again
  const sent = () => { lastSent.current = 0; };

  return { typing, onEvent, typed, sent };
}

// "Ana is typing…" by first name, like the rest of the chat; past two it doesn't list them. Nothing when nobody is
export function typingLine(people: ChatContactData[], t: TFunction) {
  const names = people.map(person => person.name.split(' ')[0]);
  if (names.length > 2) return t('chats.typingSeveral');
  if (names.length === 2) return t('chats.typingTwo', { first: names[0], second: names[1] });
  return names.length ? t('chats.typingOne', { name: names[0] }) : undefined;
}
