import type { TFunction } from 'i18next';
import { ArrowLeft, Ban, LogOut, MessagesSquare, ShieldCheck, SquarePen, UserPlus, Users, UsersRound } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getBlocked, unblockUser } from '../../api/blocks';
import { getChatMembers, getDirectChat, leaveChat } from '../../api/chats';
import { AddMembersDialog } from '../../components/chat/AddMembersDialog';
import { GroupMembersDialog } from '../../components/chat/GroupMembersDialog';
import { isBackendError } from '../../api/errors';
import { NewGroupChatDialog } from '../../components/chat/NewGroupChatDialog';
import { NewMessageDialog } from '../../components/chat/NewMessageDialog';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, buttonClass, ChatHead, ChatLayout, ChatListPane, ChatPersonRow, ChatRow, ChatRows, ChatThread, ConfirmDialog, EmptyState, IconButton, useToast, type MenuEntry } from '../../studymatch-ui';
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
  // the Blocked users page instead of a conversation (#225)
  view?: 'blocked';
}

// only students have a photo
const photoOf = (contact: ChatContactData) => contact.role === 'STUDENT' ? `/api/students/${contact.number}/pfp` : undefined;
const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString();

// an id in the address that can't be a chat: the same answer the backend gives one you aren't in, as an Error
const notFound = () => Object.assign(new Error('Not found'), { code: 'NOT_FOUND' });

// a group chat's picture, a new one isn't served from the cache
const pictureOf = (chat: ChatSummaryData) => chat.picture ? `/api/chats/${chat.chatId}/picture?v=${chat.pictureUpdatedAt ?? ''}` : undefined;

// Chats: the list (#201) and the open conversation; this page starts DMs (#199) and group chats (#227)
export function ChatsPage({ me, profilePath, view }: ChatsPageProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { number, chatId } = useParams();
  const [choosing, setChoosing] = useState(false);
  const [creating, setCreating] = useState(false);
  const isOpen = (chat: ChatSummaryData) => chat.contact ? String(chat.contact.number) === number : String(chat.chatId) === chatId;
  const { chats, error, add, remove, reload } = useChatList(message => message.author.number === me, isOpen);
  const blocks = useRemoteData('blocks', getBlocked);
  const blocked: ReadonlySet<number> = new Set(blocks.data?.map(person => person.number));
  // blocking or unblocking takes a DM off the list or brings it back, and nothing is pushed about it
  const blocksChanged = () => { blocks.retry(); void reload(); };
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
            preview={previewOf(chat, me, blocked, t)} time={chat.lastMessage ? when(chat.lastMessage.sentAt) : undefined} dateTime={chat.lastMessage?.sentAt}
            unread={chat.unread} unreadLabel={t('chats.unread', { count: chat.unread })} current={isOpen(chat)}
            online={!!chat.contact && online.has(chat.contact.number)} onlineLabel={t('chats.online')} />
        ))}
      </ChatRows>
    );
  };

  const thread = () => {
    if (view === 'blocked') return <BlockedUsers people={blocks.data} error={blocks.error} onChanged={blocksChanged} />;
    if (number) return <DirectConversation key={number} number={Number(number)} me={me} profilePath={profilePath} online={online} blocked={blocked} onBlocksChanged={blocksChanged} />;
    if (chatId) {
      return <GroupConversation key={chatId} chatId={Number(chatId)} me={me} profilePath={profilePath}
        summary={chats?.find(chat => !chat.contact && String(chat.chatId) === chatId)} listLoaded={chats !== null} listError={error} online={online} blocked={blocked}
        onLeft={() => { navigate('/chats'); remove(Number(chatId)); }} />;
    }
    return (
      <ChatThread label={t('chats.conversation')}>
        <EmptyState compact icon={MessagesSquare} title={t('chats.pickChat')} body={t('chats.pickChatBody')} />
      </ChatThread>
    );
  };

  return (
    <>
      <ChatLayout label={t('chats.title')} view={number || chatId || view ? 'thread' : 'list'}
        list={(
          <ChatListPane title={t('chats.title')} actions={newMessage}
            foot={<Link to="/chats/blocked" className="sm-chat__link" aria-current={view === 'blocked' ? 'page' : undefined}><Ban size={15} aria-hidden />{t('chats.blockedUsers')}</Link>}>
            {rows()}
          </ChatListPane>
        )}
        thread={thread()} />
      <NewMessageDialog open={choosing} onClose={() => setChoosing(false)}
        onPick={contact => { setChoosing(false); navigate(`/chats/direct/${contact.number}`); }} />
      <NewGroupChatDialog open={creating} onClose={() => setCreating(false)}
        onCreated={chat => { setCreating(false); add(chat); navigate(`/chats/group/${chat.chatId}`); }} />
    </>
  );
}

// a line of the latest message: "You: …" for yours, the first name in a group chat. Someone you blocked
// doesn't show, like their messages in the chat (GR9)
function previewOf(chat: ChatSummaryData, me: number, blocked: ReadonlySet<number>, t: TFunction) {
  const message: MessageData | null = chat.lastMessage;
  if (!message) return t('chats.noMessagesYet');
  if (message.author.number !== me && blocked.has(message.author.number)) return t('chats.fromBlocked');
  const text = lineOf(message, t);
  if (message.author.number === me) return t('chats.byYou', { text });
  if (!chat.contact) return t('chats.byThem', { name: message.author.name.split(' ')[0], text });
  return text;
}

// the DM with one person: found by their number, then its messages and the message box
interface DirectConversationProps extends Pick<ChatsPageProps, 'me' | 'profilePath'> {
  number: number;
  online: ReadonlySet<number>;
  blocked: ReadonlySet<number>;
  onBlocksChanged: () => void;
}

function DirectConversation({ number, me, profilePath, online, blocked, onBlocksChanged }: DirectConversationProps) {
  const { t } = useTranslation();
  const load = useCallback(() => Number.isInteger(number) ? getDirectChat(number) : Promise.reject(notFound()), [number]);
  const chat = useRemoteData(`direct-chat:${number}`, load);

  if (chat.error !== undefined) return <ChatNotOpened error={chat.error} />;
  if (chat.loading || !chat.data) {
    return <ChatThread label={t('chats.conversation')}><DataLoadState /></ChatThread>;
  }

  // unblocking here opens the DM again, with its history, so it's asked for again
  return <Conversation chat={{ kind: 'direct', chat: chat.data }} me={me} profilePath={profilePath} online={online} blocked={blocked}
    onBlocksChanged={() => { onBlocksChanged(); if (chat.data?.blocked) chat.retry(); }} />;
}

interface GroupConversationProps extends Pick<ChatsPageProps, 'me' | 'profilePath'> {
  chatId: number;
  // its row in the chat list, with its name and picture, kept up to date by the list
  summary?: ChatSummaryData;
  listLoaded: boolean;
  // the list couldn't load, so its row never comes: shown here too instead of loading forever
  listError?: unknown;
  online: ReadonlySet<number>;
  // the people you blocked, their messages are hidden behind a click (GR9)
  blocked: ReadonlySet<number>;
  // you left it: the list opens without it (#233)
  onLeft: () => void;
}

// a group chat by its id: its members, then its messages and the message box (#227)
function GroupConversation({ chatId, me, profilePath, summary, listLoaded, listError, online, blocked, onLeft }: GroupConversationProps) {
  const { t } = useTranslation();
  const load = useCallback(() => Number.isInteger(chatId) ? getChatMembers(chatId) : Promise.reject(notFound()), [chatId]);
  const members = useRemoteData(`chat-members:${chatId}`, load);
  // the members after you added people here (#229)
  const [latest, setLatest] = useState<ChatContactData[]>();
  const [adding, setAdding] = useState(false);
  const [listing, setListing] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const toast = useToast();
  // its row while it was in your list: if it goes while it's open, you were taken out of it (#231)
  const [seen, setSeen] = useState(summary);
  if (summary && summary !== seen) setSeen(summary);

  if (listLoaded && !summary && seen) return <NoLongerIn name={seen.name} />;
  // not in your list once it has loaded: it isn't yours, or it isn't there anymore
  const error = members.error ?? listError ?? (listLoaded && !summary ? notFound() : undefined);
  if (error !== undefined) return <ChatNotOpened error={error} />;
  if (members.loading || !members.data || !summary) {
    return <ChatThread label={t('chats.conversation')}><DataLoadState /></ChatThread>;
  }

  // an assignment group's members follow its group, nobody changes them by hand (#236)
  // and its members leave it only by leaving the group
  const menuItems: MenuEntry[] = [
    { label: t('chats.members'), icon: Users, onSelect: () => setListing(true) },
    ...(summary.assignment ? [] : [
      { label: t('chats.addMembers'), icon: UserPlus, onSelect: () => setAdding(true) },
      { type: 'separator' as const },
      { label: t('chats.leave'), icon: LogOut, danger: true, onSelect: () => setLeaving(true) },
    ]),
  ];
  const everyone = latest ?? members.data;
  // the owner is the first member, and whoever's been there longest takes over; the last one deletes it
  const leaveBody = () => {
    if (everyone.length <= 1) return t('chats.leaveLastBody');
    if (everyone[0].number !== me) return t('chats.leaveBody');
    return t('chats.leaveOwnerBody', { name: everyone[1].name });
  };
  const leave = async () => {
    setLeaving(false);
    try {
      await leaveChat(chatId);
      toast.success(t('chats.leftToast', { name: summary.name }));
      onLeft();
    } catch (failure) {
      toast.danger(isBackendError(failure) ? t(`errors.${failure.code}`, failure.params ?? {}) : t('common.unexpectedError'));
    }
  };
  return (
    <>
      <Conversation chat={{ kind: 'group', chatId, name: summary.name, pictureSrc: pictureOf(summary), members: latest ?? members.data }} me={me}
        profilePath={profilePath} online={online} blocked={blocked} menuItems={menuItems} />
      <AddMembersDialog open={adding} chatId={chatId} name={summary.name} onClose={() => setAdding(false)}
        onAdded={(now, added) => {
          setAdding(false);
          setLatest(now);
          toast.success(t('chats.membersAdded', { count: added.length, name: added[0]?.name }));
        }} />
      <GroupMembersDialog open={listing} chatId={chatId} name={summary.name} me={me} fixed={summary.assignment}
        onClose={() => setListing(false)} onChanged={setLatest} />
      <ConfirmDialog open={leaving} onClose={() => setLeaving(false)} tone="danger" icon={LogOut}
        title={t('chats.leaveTitle', { name: summary.name })} description={leaveBody()} confirmLabel={t('chats.leave')} onConfirm={leave} />
    </>
  );
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

// the open group chat left your list: someone removed you, or you left it in another tab (#231)
function NoLongerIn({ name }: { name: string }) {
  const { t } = useTranslation();
  return (
    <ChatThread label={t('chats.conversation')}>
      <EmptyState compact icon={UsersRound} title={t('chats.noLongerIn', { name })} body={t('chats.noLongerInBody')}
        action={<Link to="/chats" className={buttonClass({ variant: 'secondary', size: 'sm' })}><ArrowLeft size={16} aria-hidden />{t('chats.back')}</Link>} />
    </ChatThread>
  );
}

// the people you blocked, with Unblock (#225). They aren't told either way (GR9)
function BlockedUsers({ people, error, onChanged }: { people?: ChatContactData[]; error: unknown; onChanged: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const toast = useToast();
  const unblock = (person: ChatContactData) => unblockUser(person.number).then(
    () => { toast.success(t('chats.unblockedToast', { name: person.name })); onChanged(); },
    failure => { toast.danger(isBackendError(failure) ? t(`errors.${failure.code}`, failure.params ?? {}) : t('common.unexpectedError')); },
  );

  const body = () => {
    if (error !== undefined) return <Banner tone="danger">{isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError')}</Banner>;
    if (!people) return <DataLoadState />;
    if (!people.length) return <EmptyState compact icon={ShieldCheck} title={t('chats.noOneBlocked')} body={t('chats.noOneBlockedBody')} />;
    return (
      <ul className="sm-chat__blocked-list" aria-label={t('chats.blockedUsers')}>
        {people.map(person => (
          <ChatPersonRow key={person.number} name={person.name} avatarSrc={photoOf(person)}
            subtitle={t('chats.contactLine', { role: t(`roles.${person.role}`), number: person.number })}
            action={<Button size="sm" variant="secondary" onClick={() => void unblock(person)} aria-label={t('chats.unblockPerson', { name: person.name })}>{t('chats.unblock')}</Button>} />
        ))}
      </ul>
    );
  };

  return (
    <ChatThread label={t('chats.blockedUsers')}
      head={<ChatHead name={t('chats.blockedUsers')} subtitle={t('chats.blockedUsersSub')} backLabel={t('chats.back')} onBack={() => navigate('/chats')} noAvatar />}>
      <div className="sm-chat__blocked">{body()}</div>
    </ChatThread>
  );
}
