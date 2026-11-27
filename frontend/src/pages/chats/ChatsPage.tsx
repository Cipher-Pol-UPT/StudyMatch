import type { TFunction } from 'i18next';
import { ArrowLeft, MessagesSquare, SquarePen, UsersRound } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getChatMembers, getDirectChat } from '../../api/chats';
import { isBackendError } from '../../api/errors';
import { NewGroupChatDialog } from '../../components/chat/NewGroupChatDialog';
import { NewMessageDialog } from '../../components/chat/NewMessageDialog';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, buttonClass, ChatLayout, ChatListPane, ChatRow, ChatRows, ChatThread, EmptyState, IconButton } from '../../studymatch-ui';
import { type ChatContactData, type ChatSummaryData, type MessageData } from '../../types/chat';
import { Conversation } from './Conversation';
import { lineOf } from './messageText';
import { useChatList } from './useChatList';
import { useOnline } from './useOnline';

export interface ChatsPageProps {
  // your number, to tell your own messages apart in the list
  me: number;
  // where someone's profile is, for the people who have one you can open
  profilePath?: (contact: ChatContactData) => string | undefined;
}

// only students have a photo
const photoOf = (contact: ChatContactData) => contact.role === 'STUDENT' ? `/api/students/${contact.number}/pfp` : undefined;
const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString();

// an id in the address that can't be a chat: the same answer the backend gives one you aren't in, as an Error
const notFound = () => Object.assign(new Error('Not found'), { code: 'NOT_FOUND' });

// a group chat's picture, a new one isn't served from the cache
const pictureOf = (chat: ChatSummaryData) => chat.picture ? `/api/chats/${chat.chatId}/picture?v=${chat.pictureUpdatedAt ?? ''}` : undefined;

// Chats: the list (#201) and the open conversation; this page starts DMs (#199) and group chats (#227)
export function ChatsPage({ me, profilePath }: ChatsPageProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { number, chatId } = useParams();
  const [choosing, setChoosing] = useState(false);
  const [creating, setCreating] = useState(false);
  const isOpen = (chat: ChatSummaryData) => chat.contact ? String(chat.contact.number) === number : String(chat.chatId) === chatId;
  const { chats, error, add } = useChatList(message => message.author.number === me, isOpen);
  const online = useOnline();

  const newMessage = (
    <>
      <IconButton icon={UsersRound} label={t('chats.newGroupChat')} title={t('chats.newGroupChat')} variant="ghost" onClick={() => setCreating(true)} />
      <Button size="sm" icon={SquarePen} onClick={() => setChoosing(true)}>{t('chats.newMessage')}</Button>
    </>
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
            avatarSrc={chat.contact ? photoOf(chat.contact) : pictureOf(chat)}
            href={chat.contact ? `/chats/direct/${chat.contact.number}` : `/chats/group/${chat.chatId}`}
            preview={previewOf(chat, me, t)} time={chat.lastMessage ? when(chat.lastMessage.sentAt) : undefined} dateTime={chat.lastMessage?.sentAt}
            unread={chat.unread} unreadLabel={t('chats.unread', { count: chat.unread })} current={isOpen(chat)}
            online={!!chat.contact && online.has(chat.contact.number)} onlineLabel={t('chats.online')} />
        ))}
      </ChatRows>
    );
  };

  const thread = () => {
    if (number) return <DirectConversation key={number} number={Number(number)} me={me} profilePath={profilePath} online={online} />;
    if (chatId) {
      return <GroupConversation key={chatId} chatId={Number(chatId)} me={me} profilePath={profilePath}
        summary={chats?.find(chat => !chat.contact && String(chat.chatId) === chatId)} listLoaded={chats !== null} listError={error} online={online} />;
    }
    return (
      <ChatThread label={t('chats.conversation')}>
        <EmptyState compact icon={MessagesSquare} title={t('chats.pickChat')} body={t('chats.pickChatBody')} />
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
      <NewGroupChatDialog open={creating} onClose={() => setCreating(false)}
        onCreated={chat => { setCreating(false); add(chat); navigate(`/chats/group/${chat.chatId}`); }} />
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
function DirectConversation({ number, me, profilePath, online }: { number: number; online: ReadonlySet<number> } & Pick<ChatsPageProps, 'me' | 'profilePath'>) {
  const { t } = useTranslation();
  const load = useCallback(() => Number.isInteger(number) ? getDirectChat(number) : Promise.reject(notFound()), [number]);
  const chat = useRemoteData(`direct-chat:${number}`, load);

  if (chat.error !== undefined) return <ChatNotOpened error={chat.error} />;
  if (chat.loading || !chat.data) {
    return <ChatThread label={t('chats.conversation')}><DataLoadState /></ChatThread>;
  }

  return <Conversation chat={{ kind: 'direct', chat: chat.data }} me={me} profilePath={profilePath} online={online} />;
}

interface GroupConversationProps extends Pick<ChatsPageProps, 'me' | 'profilePath'> {
  chatId: number;
  // its row in the chat list, with its name and picture, kept up to date by the list
  summary?: ChatSummaryData;
  listLoaded: boolean;
  // the list couldn't load, so its row never comes: shown here too instead of loading forever
  listError?: unknown;
  online: ReadonlySet<number>;
}

// a group chat by its id: its members, then its messages and the message box (#227)
function GroupConversation({ chatId, me, profilePath, summary, listLoaded, listError, online }: GroupConversationProps) {
  const { t } = useTranslation();
  const load = useCallback(() => Number.isInteger(chatId) ? getChatMembers(chatId) : Promise.reject(notFound()), [chatId]);
  const members = useRemoteData(`chat-members:${chatId}`, load);

  // not in your list once it has loaded: it isn't yours, or it isn't there anymore
  const error = members.error ?? listError ?? (listLoaded && !summary ? notFound() : undefined);
  if (error !== undefined) return <ChatNotOpened error={error} />;
  if (members.loading || !members.data || !summary) {
    return <ChatThread label={t('chats.conversation')}><DataLoadState /></ChatThread>;
  }

  return <Conversation chat={{ kind: 'group', chatId, name: summary.name, pictureSrc: pictureOf(summary), members: members.data }} me={me} profilePath={profilePath} online={online} />;
}

// a chat that can't be opened: why, and the way back to the list
function ChatNotOpened({ error }: { error: unknown }) {
  const { t } = useTranslation();
  return (
    <ChatThread label={t('chats.conversation')}>
      <Banner tone="danger">{isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError')}</Banner>
      <Link to="/chats" className={buttonClass({ variant: 'secondary', size: 'sm' })}><ArrowLeft size={16} aria-hidden />{t('chats.back')}</Link>
    </ChatThread>
  );
}
