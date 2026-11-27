import { useEffect, useState } from 'react';
import { getOnlineContacts } from '../../api/chats';
import { useChatListEvents, useChatSocketConnected } from '../../hooks/useChatEvents';

const NOBODY: ReadonlySet<number> = new Set();

// who you share a chat with has the app open (#223): asked when the connection comes up, then USER_ONLINE and
// USER_OFFLINE on /user/queue/chats. A new chat's people come with nothing pushed, so CHAT_ADDED asks again.
// While the connection is down nobody shows as online, nothing would tell us they left
export function useOnline(enabled = true) {
  const connected = useChatSocketConnected();
  const [online, setOnline] = useState<ReadonlySet<number>>(NOBODY);
  const [asked, setAsked] = useState(0);

  useEffect(() => {
    if (!enabled || !connected) return;
    let current = true;
    getOnlineContacts().then(numbers => { if (current) setOnline(new Set(numbers)); }, () => {});
    return () => { current = false; };
  }, [enabled, connected, asked]);

  useChatListEvents(event => {
    if (event.type === 'CHAT_ADDED') setAsked(count => count + 1);
    if (event.type !== 'USER_ONLINE' && event.type !== 'USER_OFFLINE') return;
    const number = event.payload as number;
    setOnline(current => {
      const next = new Set(current);
      if (event.type === 'USER_ONLINE') next.add(number); else next.delete(number);
      return next;
    });
  }, enabled);

  return enabled && connected ? online : NOBODY;
}
