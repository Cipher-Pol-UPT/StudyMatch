import type { TFunction } from 'i18next';
import { ArrowLeft, MessagesSquare, SquarePen } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getDirectChat } from '../../api/chats';
import { isBackendError } from '../../api/errors';
import { NewMessageDialog } from '../../components/chat/NewMessageDialog';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, buttonClass, ChatLayout, ChatListPane, ChatRow, ChatRows, ChatThread, EmptyState } from '../../studymatch-ui';
import { type ChatContactData, type ChatSummaryData, type MessageData } from '../../types/chat';
import { Conversation } from './Conversation';
import { lineOf } from './messageText';
import { useChatList } from './useChatList';

export interface ChatsPageProps {
  // your number, to tell your own messages apart in the list
  me: number;
  // where someone's profile is, for the people who have one you can open
  profilePath?: (contact: ChatContactData) => string | undefined;
}

// only students have a photo
const photoOf = (contact: ChatContactData) => contact.role === 'STUDENT' ? `/api/students/${contact.number}/pfp` : undefined;
const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString();

// Chats: the list (#201) and the open conversation; this page starts DMs (#199)
export function ChatsPage({ me, profilePath }: ChatsPageProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { number, chatId } = useParams();
  const [choosing, setChoosing] = useState(false);
  const isOpen = (chat: ChatSummaryData) => chat.contact ? String(chat.contact.number) === number : String(chat.chatId) === chatId;
  const { chats, error } = useChatList(message => message.author.number === me, isOpen);

  const newMessage = (
    <Button size="sm" icon={SquarePen} onClick={() => setChoosing(true)}>{t('chats.newMessage')}</Button>
  );

  // today it's the time, yesterday says so, before that the day
  const when = (sentAt: string) => {
    const date = new Date(sentAt);
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    if (sameDay(date, new Date())) return new Intl.DateTimeFormat(i18n.language, { hour: '2-digit', minute: '2-digit', hour12: false }).format(date);
    if (sameDay(date, yesterday)) return t('chats.yesterday');
    return new Intl.DateTimeFormat(i18n.language, { day: 'numeric', month: 'short' }).format(date);
  };

  const rows = () => {
    if (error !== undefined) return <Banner tone="danger">{isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError')}</Banner>;
    if (!chats) return <DataLoadState />;
    if (!chats.length) return <EmptyState compact icon={MessagesSquare} title={t('chats.noChats')} body={t('chats.noChatsBody')} action={newMessage} />;
    return (
      <ChatRows label={t('chats.title')}>
        {chats.map(chat => (
          <ChatRow key={chat.chatId} name={chat.name} group={!chat.contact} linkAs={Link}
            avatarSrc={chat.contact ? photoOf(chat.contact) : chat.picture ? `/api/chats/${chat.chatId}/picture?v=${chat.pictureUpdatedAt ?? ''}` : undefined}
            href={chat.contact ? `/chats/direct/${chat.contact.number}` : `/chats/group/${chat.chatId}`}
            preview={previewOf(chat, me, t)} time={chat.lastMessage ? when(chat.lastMessage.sentAt) : undefined} dateTime={chat.lastMessage?.sentAt}
            unread={chat.unread} unreadLabel={t('chats.unread', { count: chat.unread })} current={isOpen(chat)} />
        ))}
      </ChatRows>
    );
  };

  const thread = () => {
    if (number) return <DirectConversation key={number} number={Number(number)} profilePath={profilePath} />;
    // group chats open with their own issues (#227); until then the list shows them
    const pick = chatId ? { title: t('chats.groupSoon'), body: t('chats.groupSoonBody') } : { title: t('chats.pickChat'), body: t('chats.pickChatBody') };
    return (
      <ChatThread label={t('chats.conversation')}>
        <EmptyState compact icon={MessagesSquare} title={pick.title} body={pick.body} />
      </ChatThread>
    );
  };

  return (
    <>
      <ChatLayout label={t('chats.title')} view={number || chatId ? 'thread' : 'list'}
        list={<ChatListPane title={t('chats.title')} actions={newMessage}>{rows()}</ChatListPane>}
        thread={thread()} />
      <NewMessageDialog open={choosing} onClose={() => setChoosing(false)}
        onPick={contact => { setChoosing(false); navigate(`/chats/direct/${contact.number}`); }} />
    </>
  );
}

// a line of the latest message: "You: …" for yours, the first name in a group chat
function previewOf(chat: ChatSummaryData, me: number, t: TFunction) {
  const message: MessageData | null = chat.lastMessage;
  if (!message) return t('chats.noMessagesYet');
  const text = lineOf(message, t);
  if (message.author.number === me) return t('chats.byYou', { text });
  if (!chat.contact) return t('chats.byThem', { name: message.author.name.split(' ')[0], text });
  return text;
}

// the DM with one person: found by their number, then its messages and the message box
function DirectConversation({ number, profilePath }: { number: number } & Pick<ChatsPageProps, 'profilePath'>) {
  const { t } = useTranslation();
  const load = useCallback(() => Number.isInteger(number) ? getDirectChat(number) : Promise.reject({ code: 'NOT_FOUND' }), [number]);
  const chat = useRemoteData(`direct-chat:${number}`, load);

  if (chat.error !== undefined) {
    return (
      <ChatThread label={t('chats.conversation')}>
        <Banner tone="danger">{isBackendError(chat.error) ? t(`errors.${chat.error.code}`, chat.error.params ?? {}) : t('common.unexpectedError')}</Banner>
        <Link to="/chats" className={buttonClass({ variant: 'secondary', size: 'sm' })}><ArrowLeft size={16} aria-hidden />{t('chats.back')}</Link>
      </ChatThread>
    );
  }
  if (chat.loading || !chat.data) {
    return <ChatThread label={t('chats.conversation')}><DataLoadState /></ChatThread>;
  }

  return <Conversation chat={chat.data} profilePath={profilePath} />;
}
