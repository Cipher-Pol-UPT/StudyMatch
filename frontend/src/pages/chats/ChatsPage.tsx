import { ArrowLeft, MessagesSquare, SquarePen } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { TFunction } from 'i18next';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getDirectChat, sendDirectMessage } from '../../api/chats';
import { isBackendError } from '../../api/errors';
import { NewMessageDialog } from '../../components/chat/NewMessageDialog';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useRemoteData } from '../../hooks/useRemoteData';
import { Banner, Button, buttonClass, ChatComposer, ChatHead, ChatLayout, ChatListPane, ChatMessage, ChatMessages, ChatStart, ChatThread, EmptyState } from '../../studymatch-ui';
import { MESSAGE_MAX_LENGTH, type ChatContactData, type DirectChatData } from '../../types/chat';
import { newMessageKey, useConversation, type ThreadMessage } from './conversations';

// Chats: the list (#201) and the open conversation; this page starts DMs (#199)
export interface ChatsPageProps {
  // where someone's profile is, for the people who have one you can open
  profilePath?: (contact: ChatContactData) => string | undefined;
}

export function ChatsPage({ profilePath }: ChatsPageProps = {}) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { number } = useParams();
  const [choosing, setChoosing] = useState(false);
  const newMessage = (
    <Button size="sm" icon={SquarePen} onClick={() => setChoosing(true)}>{t('chats.newMessage')}</Button>
  );

  return (
    <>
      <ChatLayout label={t('chats.title')} view={number ? 'thread' : 'list'}
        list={(
          <ChatListPane title={t('chats.title')} actions={newMessage}>
            {/* the DMs only show here after their first message, which the list brings in #201 */}
            <EmptyState compact icon={MessagesSquare} title={t('chats.noChats')} body={t('chats.noChatsBody')} />
          </ChatListPane>
        )}
        thread={number ? <DirectConversation key={number} number={Number(number)} profilePath={profilePath} /> : (
          <ChatThread label={t('chats.conversation')}>
            <EmptyState compact icon={MessagesSquare} title={t('chats.pickChat')} body={t('chats.pickChatBody')} />
          </ChatThread>
        )} />
      <NewMessageDialog open={choosing} onClose={() => setChoosing(false)}
        onPick={contact => { setChoosing(false); navigate(`/chats/direct/${contact.number}`); }} />
    </>
  );
}

// the DM with one person: empty with a hint to say hi until the first message, then what's sent in it (its history is #203)
function DirectConversation({ number, profilePath }: { number: number } & ChatsPageProps) {
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

  return <LoadedConversation chat={chat.data} profilePath={profilePath} />;
}

// messages closer than this are one run: only the first shows when
const RUN_MS = 5 * 60 * 1000;

function LoadedConversation({ chat: { chatId, contact }, profilePath }: { chat: DirectChatData } & ChatsPageProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [conversation, update] = useConversation(contact.number);
  const scroll = useRef<HTMLDivElement>(null);
  const role = t(`roles.${contact.role}`);
  const line = t('chats.contactLine', { role, number: contact.number });
  const firstName = contact.name.split(' ')[0];
  const { messages } = conversation;
  const timeFormat = new Intl.DateTimeFormat(i18n.language, { hour: '2-digit', minute: '2-digit' });

  // the newest message stays in sight
  useEffect(() => {
    if (scroll.current) scroll.current.scrollTop = scroll.current.scrollHeight;
  }, [messages.length]);

  const deliver = (key: string, text: string) => {
    sendDirectMessage(contact.number, text).then(
      message => update(current => ({
        ...current,
        messages: current.messages.map(m => m.key === key ? { key, text: message.text, sentAt: message.sentAt } : m),
      })),
      error => update(current => ({
        ...current,
        error,
        messages: current.messages.map(m => m.key === key ? { ...m, status: 'failed' } : m),
      })),
    );
  };
  const send = () => {
    const text = conversation.draft.trim();
    const key = newMessageKey();
    update(current => ({
      draft: '',
      error: undefined,
      messages: [...current.messages, { key, text, sentAt: new Date().toISOString(), status: 'sending' }],
    }));
    deliver(key, text);
  };
  const retry = (message: ThreadMessage) => {
    update(current => ({ ...current, error: undefined, messages: current.messages.map(m => m.key === message.key ? { ...m, status: 'sending' } : m) }));
    deliver(message.key, message.text);
  };
  const discard = (message: ThreadMessage) =>
    update(current => ({ ...current, error: undefined, messages: current.messages.filter(m => m.key !== message.key) }));

  // a new DM starts here; one you already had shows its history above with #203
  const start = chatId === null
    ? <ChatStart name={contact.name}>{messages.length ? t('chats.startOfConversation', { name: firstName }) : t('chats.sayHi', { line, name: firstName })}</ChatStart>
    : !messages.length && <ChatStart name={contact.name} />;

  return (
    <ChatThread label={t('chats.with', { name: contact.name })} scrollRef={scroll}
      head={<ChatHead name={contact.name} subtitle={line} backLabel={t('chats.back')} onBack={() => navigate('/chats')}
        profileHref={profilePath?.(contact)} linkAs={Link} profileLabel={t('chats.openProfile', { name: contact.name })} />}
      composer={(
        <ChatComposer label={t('chats.messageLabel', { name: contact.name })} placeholder={t('chats.messageLabel', { name: firstName })}
          value={conversation.draft} onChange={draft => update(current => ({ ...current, draft }))} onSend={send}
          sendLabel={t('chats.send')} max={MESSAGE_MAX_LENGTH} error={conversation.error === undefined ? undefined : sendError(conversation.error, t)} />
      )}>
      {start}
      {messages.length > 0 && (
        <ChatMessages label={t('chats.messages')}>
          {messages.map((message, index) => {
            const previous = messages[index - 1];
            const sentAt = new Date(message.sentAt);
            return (
              <ChatMessage key={message.key} mine text={message.text}
                first={!previous || sentAt.getTime() - new Date(previous.sentAt).getTime() > RUN_MS}
                time={timeFormat.format(sentAt)} dateTime={message.sentAt} status={message.status}
                statusLabel={message.status === 'failed' ? t('chats.notSent') : t('chats.sending')}
                retryLabel={t('chats.retry')} discardLabel={t('chats.discard')}
                onRetry={() => retry(message)} onDiscard={() => discard(message)} />
            );
          })}
        </ChatMessages>
      )}
    </ChatThread>
  );
}

// the text's own problem (empty, too long) when the backend names it, otherwise the error's code
function sendError(error: unknown, t: TFunction) {
  if (!isBackendError(error)) return t('common.unexpectedError');
  const field = error.fieldErrors?.find(f => f.field === 'text');
  if (field) return t(`validation.${field.code}`, { ...field.params, defaultValue: t('validation.INVALID') });
  return t(`errors.${error.code}`, error.params ?? {});
}
