import { useCallback, useEffect, useRef, useState } from 'react';
import { getChatMessages, markChatRead } from '../../api/chats';
import type { MessageData } from '../../types/chat';

export interface ChatHistory {
  // oldest first, each message once
  messages: MessageData[];
  // older messages are still there to load
  hasMore: boolean;
  loading: boolean;
  loadingOlder: boolean;
  error?: unknown;
}

// a message can come twice (the send's answer and the live event), the list keeps one by id
function merge(current: MessageData[], more: MessageData[]) {
  const byId = new Map(current.map(message => [message.id, message]));
  more.forEach(message => byId.set(message.id, message));
  return [...byId.values()].sort((a, b) => a.id - b.id);
}

const ignore = () => undefined;

// an open chat's messages: the 50 newest when it opens, 50 more each time loadOlder is called (#203).
// Opening it marks it as read. A chat that doesn't exist yet (a DM before its first message) has none to load
export function useChatHistory(chatId: number | null) {
  // something came while the tab was hidden: it's marked as read when you come back, not before you've seen it
  const unseen = useRef(false);
  const markRead = useCallback(() => {
    if (chatId === null) return;
    if (document.visibilityState === 'visible') markChatRead(chatId).catch(ignore);
    else unseen.current = true;
  }, [chatId]);

  useEffect(() => {
    const visible = () => {
      if (document.visibilityState !== 'visible' || !unseen.current) return;
      unseen.current = false;
      markRead();
    };
    document.addEventListener('visibilitychange', visible);
    return () => document.removeEventListener('visibilitychange', visible);
  }, [markRead]);

  const [history, setHistory] = useState<ChatHistory>(() => ({ messages: [], hasMore: false, loading: chatId !== null, loadingOlder: false }));
  // what's on screen, for loadOlder to read without being remade on every change
  const current = useRef(history);
  useEffect(() => { current.current = history; });

  useEffect(() => {
    if (chatId === null) return undefined;
    let open = true;
    getChatMessages(chatId).then(
      page => {
        if (!open) return;
        setHistory(state => ({ ...state, messages: merge(state.messages, page.messages), hasMore: page.hasMore, loading: false }));
        markRead();
      },
      error => { if (open) setHistory(state => ({ ...state, loading: false, error })); },
    );
    return () => { open = false; };
  }, [chatId, markRead]);

  const loadOlder = useCallback(() => {
    const { messages, hasMore, loading, loadingOlder } = current.current;
    if (chatId === null || !hasMore || loading || loadingOlder || !messages.length) return false;
    current.current = { ...current.current, loadingOlder: true };
    setHistory(state => ({ ...state, loadingOlder: true }));
    getChatMessages(chatId, messages[0].id).then(
      page => setHistory(state => ({ ...state, messages: merge(page.messages, state.messages), hasMore: page.hasMore, loadingOlder: false })),
      // scrolling up again tries again
      () => setHistory(state => ({ ...state, loadingOlder: false })),
    );
    return true;
  }, [chatId]);

  // a new message, or the answer to one you sent
  const add = useCallback((message: MessageData) => setHistory(state => ({ ...state, messages: merge(state.messages, [message]) })), []);
  // a newer version of a message (edited). One that isn't loaded stays out, or it would show out of place above a gap
  const replace = useCallback((message: MessageData) => setHistory(state => state.messages.some(m => m.id === message.id)
    ? { ...state, messages: state.messages.map(m => m.id === message.id ? message : m) }
    : state), []);
  const remove = useCallback((id: number) => setHistory(state => ({ ...state, messages: state.messages.filter(message => message.id !== id) })), []);

  return { history, loadOlder, add, replace, remove, markRead };
}
