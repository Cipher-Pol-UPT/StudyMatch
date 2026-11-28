import { useCallback, useEffect, useState } from 'react';
import { getUnreadCount } from '../api/chats';
import { useChatListEvents } from './useChatEvents';

// what changes how many messages you haven't read
const CHANGES = new Set(['CHAT_UPDATED', 'CHAT_READ', 'CHAT_ADDED', 'CHAT_REMOVED', 'MESSAGE_DELETED']);

// the unread messages across every chat, for the side menu's Chats item (#201).
// Asked again whenever the chat list changes; off for the roles that have no chats
export function useUnreadChats(enabled: boolean) {
  const [count, setCount] = useState(0);
  const refresh = useCallback(() => {
    // anything but a number (a fake backend in a test) counts as none
    if (enabled) getUnreadCount().then(n => setCount(typeof n === 'number' ? n : 0), () => undefined);
  }, [enabled]);

  useEffect(() => { refresh(); }, [refresh]);
  useChatListEvents(event => { if (CHANGES.has(event.type)) refresh(); }, enabled);

  return enabled ? count : 0;
}
