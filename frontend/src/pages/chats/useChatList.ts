import { useCallback, useEffect, useState } from 'react';
import { getChats } from '../../api/chats';
import { useChatListEvents } from '../../hooks/useChatEvents';
import type { ChatEditedData, ChatSummaryData, MessageData, MessageDeletedData } from '../../types/chat';

export interface ChatList {
  // newest message first; null while it loads
  chats: ChatSummaryData[] | null;
  error?: unknown;
}

// the chat list (#201), kept up to date from /user/queue/chats without reloading the page.
// isMine tells your own messages apart, they never count as unread; the open chat doesn't either,
// it marks itself as read and CHAT_READ clears it
export function useChatList(isMine: (message: MessageData) => boolean, isOpen: (chat: ChatSummaryData) => boolean) {
  const [list, setList] = useState<ChatList>({ chats: null });

  const reload = useCallback(() => getChats().then(
    chats => setList({ chats }),
    error => setList(current => ({ ...current, error })),
  ), []);

  useEffect(() => { void reload(); }, [reload]);

  const update = (change: (chats: ChatSummaryData[]) => ChatSummaryData[]) =>
    setList(current => current.chats ? { ...current, chats: change(current.chats) } : current);

  useChatListEvents(event => {
    switch (event.type) {
      case 'CHAT_UPDATED': {
        const message = event.payload as MessageData;
        const known = list.chats?.find(chat => chat.chatId === message.chatId);
        // a chat that's new to the list (their first message in a DM): the backend has its row
        if (!known) return void reload();
        const unread = isMine(message) || isOpen(known) ? known.unread : known.unread + 1;
        return update(chats => [{ ...known, lastMessage: message, unread }, ...chats.filter(chat => chat.chatId !== known.chatId)]);
      }
      case 'CHAT_ADDED': {
        const added = event.payload as ChatSummaryData;
        return update(chats => [added, ...chats.filter(chat => chat.chatId !== added.chatId)]);
      }
      case 'CHAT_READ':
        return update(chats => chats.map(chat => chat.chatId === event.payload ? { ...chat, unread: 0 } : chat));
      case 'CHAT_REMOVED':
        return update(chats => chats.filter(chat => chat.chatId !== event.payload));
      case 'CHAT_EDITED': {
        const edited = event.payload as ChatEditedData;
        return update(chats => chats.map(chat => chat.chatId === edited.chatId ? { ...chat, ...edited } : chat));
      }
      case 'MESSAGE_EDITED': {
        const message = event.payload as MessageData;
        return update(chats => chats.map(chat => chat.lastMessage?.id === message.id ? { ...chat, lastMessage: message } : chat));
      }
      case 'MESSAGE_DELETED': {
        // the preview and the unread count may both change, the backend works them out again
        const { chatId } = event.payload as MessageDeletedData;
        if (list.chats?.some(chat => chat.chatId === chatId)) void reload();
        return undefined;
      }
      default:
        return undefined;
    }
  });

  return list;
}
