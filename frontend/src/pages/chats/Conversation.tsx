import type { TFunction } from 'i18next';
import { ArrowLeft } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { deleteMessage, editMessage, markChatRead, sendDirectMessage } from '../../api/chats';
import { isBackendError } from '../../api/errors';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useChatEvents, useChatListEvents } from '../../hooks/useChatEvents';
import { Banner, buttonClass, ChatComposer, ChatDay, ChatHead, ChatJump, ChatMessage, ChatMessageEditor, ChatMessages, ChatOlder, ChatStart, ChatThread, ConfirmDialog, useToast } from '../../studymatch-ui';
import { MESSAGE_MAX_LENGTH, type ChatContactData, type DirectChatData, type MessageData, type MessageDeletedData } from '../../types/chat';
import { newMessageKey, useConversation, type ThreadMessage } from './conversations';
import { useChatHistory } from './useChatHistory';

export interface ConversationProps {
  chat: DirectChatData;
}

// messages closer than this are one run: only the first shows who and when
const RUN_MS = 5 * 60 * 1000;
// this close to an edge counts as being at it: the bottom keeps up with new messages, the top loads older ones
const EDGE_PX = 80;

// one message on screen: from the history, or one of yours still sending or failed
interface Item {
  key: string;
  text: string;
  sentAt: string;
  author: ChatContactData;
  mine: boolean;
  editedAt?: string | null;
  // the message as the backend has it, for editing and deleting it
  message?: MessageData;
  pending?: ThreadMessage;
}

const ignore = () => undefined;
const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString();
// only students have a photo
const photoOf = (author: ChatContactData) => author.role === 'STUDENT' ? `/api/students/${author.number}/pfp` : undefined;

// a DM: its history, live messages while it's open, editing and deleting your own, and the message box (#203, #205, #207, #209)
export function Conversation({ chat }: ConversationProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { contact } = chat;
  const [conversation, update] = useConversation(contact.number);
  // the DM gets its id with its first message, yours or theirs: from then on its history loads and its live events are followed
  const [chatId, setChatId] = useState(chat.chatId);
  const { history, loadOlder, add, replace, remove } = useChatHistory(chatId);
  const toast = useToast();
  // a message you sent keeps the key it had while sending, so it doesn't jump when the backend answers
  const [keys, setKeys] = useState<ReadonlyMap<number, string>>(() => new Map());
  // a message arrived while the tab was hidden, it's marked as read when you come back
  const unseen = useRef(false);
  const [newBelow, setNewBelow] = useState(false);
  const scroll = useRef<HTMLDivElement>(null);
  const atBottom = useRef(true);
  // while older messages load, the distance from the bottom to keep, so what you're reading stays put
  const keepFromBottom = useRef<number | null>(null);
  const lastKey = useRef<string | undefined>(undefined);
  const composer = useRef<{ focus: () => void }>(null);
  // the message being edited, with its new text and why saving it failed
  const [editing, setEditing] = useState<{ id: number; text: string; error?: string; saving?: boolean } | null>(null);
  // the message waiting for Delete to be confirmed
  const [deleting, setDeleting] = useState<MessageData | null>(null);

  const line = t('chats.contactLine', { role: t(`roles.${contact.role}`), number: contact.number });
  const firstName = contact.name.split(' ')[0];
  const timeFormat = new Intl.DateTimeFormat(i18n.language, { hour: '2-digit', minute: '2-digit' });
  const dayFormat = new Intl.DateTimeFormat(i18n.language, { day: 'numeric', month: 'long', year: 'numeric' });
  const editedFormat = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeStyle: 'short' });
  // in a DM everything not from them is yours
  const isMine = (author: ChatContactData) => author.number !== contact.number;

  const items: Item[] = [
    ...history.messages.map(message => ({
      key: keys.get(message.id) ?? `message-${message.id}`, text: message.text, sentAt: message.sentAt, author: message.author,
      mine: isMine(message.author), editedAt: message.editedAt, message,
    })),
    ...conversation.messages.map(message => ({ key: message.key, text: message.text, sentAt: message.sentAt, author: contact, mine: true, pending: message })),
  ];

  // yours, confirmed: the one still sending with that text goes, and the message keeps its key
  const confirmed = (message: MessageData, key = conversation.messages.find(m => m.status === 'sending' && m.text === message.text)?.key) => {
    if (key) {
      setKeys(current => new Map(current).set(message.id, key));
      update(current => ({ ...current, messages: current.messages.filter(m => m.key !== key) }));
    }
    add(message);
  };

  // their first message creates the DM while it's open here: its id comes in your chat list's event
  useChatListEvents(event => {
    const message = event.payload as Partial<MessageData> | null;
    if (chatId === null && message?.chatId && message.author?.number === contact.number) setChatId(message.chatId);
  });

  useEffect(() => {
    const visible = () => {
      if (document.visibilityState !== 'visible' || !unseen.current || chatId === null) return;
      unseen.current = false;
      markChatRead(chatId).catch(ignore);
    };
    document.addEventListener('visibilitychange', visible);
    return () => document.removeEventListener('visibilitychange', visible);
  }, [chatId]);

  useChatEvents(chatId, event => {
    if (event.type === 'MESSAGE_EDITED') return replace(event.payload as MessageData);
    if (event.type === 'MESSAGE_DELETED') {
      const { messageId } = event.payload as MessageDeletedData;
      remove(messageId);
      // deleted in another tab while you were editing it here
      setEditing(current => current?.id === messageId ? null : current);
      return;
    }
    if (event.type !== 'MESSAGE_CREATED') return;
    const message = event.payload as MessageData;
    // yours can come before the answer to sending it
    if (isMine(message.author)) return confirmed(message);
    add(message);
    if (!atBottom.current) setNewBelow(true);
    // you're looking at it, so it's read; otherwise once you come back to the tab
    if (document.visibilityState !== 'visible') unseen.current = true;
    else if (chatId !== null) markChatRead(chatId).catch(ignore);
  });

  // older messages keep what you're reading in place; a new one at the bottom is followed when you're there or it's yours
  useLayoutEffect(() => {
    const box = scroll.current;
    if (!box) return;
    if (keepFromBottom.current !== null) {
      box.scrollTop = box.scrollHeight - keepFromBottom.current;
      if (!history.loadingOlder) keepFromBottom.current = null;
      return;
    }
    const last = items.at(-1);
    if (!last || last.key === lastKey.current) return;
    const opening = lastKey.current === undefined;
    lastKey.current = last.key;
    if (opening || last.mine || atBottom.current) box.scrollTop = box.scrollHeight;
    // a screen taller than the first page can't be scrolled up, so the older ones load straight away
    if (opening && box.clientHeight > 0 && box.scrollHeight <= box.clientHeight) older(box);
  });

  function older(box: HTMLDivElement) {
    const distance = box.scrollHeight - box.scrollTop;
    if (loadOlder()) keepFromBottom.current = distance;
  }

  const onScroll = () => {
    const box = scroll.current;
    if (!box) return;
    atBottom.current = box.scrollHeight - box.scrollTop - box.clientHeight < EDGE_PX;
    if (atBottom.current && newBelow) setNewBelow(false);
    if (box.scrollTop < EDGE_PX) older(box);
  };

  const jump = () => {
    if (scroll.current) scroll.current.scrollTop = scroll.current.scrollHeight;
    atBottom.current = true;
    setNewBelow(false);
  };

  const deliver = (key: string, text: string) => {
    sendDirectMessage(contact.number, text).then(
      message => {
        confirmed(message, key);
        setChatId(id => id ?? message.chatId);
      },
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

  const stopEditing = () => {
    setEditing(null);
    composer.current?.focus();
  };

  const startEditing = (message: MessageData) => setEditing({ id: message.id, text: message.text });
  // Arrow up in an empty message box: your last message that the backend has
  const editLast = () => {
    const last = items.findLast(item => item.mine && item.message);
    if (!last?.message) return false;
    startEditing(last.message);
    return true;
  };
  const saveEdit = (message: MessageData) => {
    // Enter again while it's saving sends nothing more
    if (!editing || editing.saving) return;
    const text = editing.text.trim();
    // nothing changed, or emptied: emptying isn't deleting, the backend refuses it like an empty message
    if (text === message.text) return stopEditing();
    setEditing({ ...editing, saving: true });
    // the answer only closes or marks this message's box, not another one you've started editing since
    const isThis = (current: { id: number } | null) => current?.id === message.id;
    editMessage(message.chatId, message.id, text).then(
      edited => {
        replace(edited);
        setEditing(current => isThis(current) ? null : current);
        if (!document.activeElement?.closest('.sm-msg__edit')) composer.current?.focus();
      },
      error => setEditing(current => isThis(current) ? { ...current!, saving: false, error: sendError(error, t) } : current),
    );
  };
  const deleteForGood = (message: MessageData) => deleteMessage(message.chatId, message.id).then(
    () => remove(message.id),
    error => { toast.danger(isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError')); },
  );
  const askToDelete = (message: MessageData, shift: boolean) => shift ? deleteForGood(message) : setDeleting(message);

  const head = (
    <ChatHead name={contact.name} avatarSrc={photoOf(contact)} subtitle={line} backLabel={t('chats.back')} onBack={() => navigate('/chats')} />
  );

  if (history.error !== undefined) {
    return (
      <ChatThread label={t('chats.with', { name: contact.name })} head={head}>
        <Banner tone="danger">{isBackendError(history.error) ? t(`errors.${history.error.code}`, history.error.params ?? {}) : t('common.unexpectedError')}</Banner>
        <Link to="/chats" className={buttonClass({ variant: 'secondary', size: 'sm' })}><ArrowLeft size={16} aria-hidden />{t('chats.back')}</Link>
      </ChatThread>
    );
  }

  const dayLabel = (date: Date) => {
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(today.getDate() - 1);
    if (sameDay(date, today)) return t('chats.today');
    if (sameDay(date, yesterday)) return t('chats.yesterday');
    return dayFormat.format(date);
  };

  // the top of the conversation once its first message is on screen, or a hint to say hi when there's none
  const start = history.loading || history.hasMore ? null
    : items.length ? <ChatStart name={contact.name} avatarSrc={photoOf(contact)}>{t('chats.startOfConversation', { name: firstName })}</ChatStart>
      : <ChatStart name={contact.name} avatarSrc={photoOf(contact)}>{t('chats.sayHi', { line, name: firstName })}</ChatStart>;

  return (
    <ChatThread label={t('chats.with', { name: contact.name })} scrollRef={scroll} onScroll={onScroll} head={head}
      overlay={newBelow && <ChatJump label={t('chats.newMessages')} onClick={jump} />}
      composer={(
        <ChatComposer label={t('chats.messageLabel', { name: contact.name })} placeholder={t('chats.messageLabel', { name: firstName })}
          value={conversation.draft} onChange={draft => update(current => ({ ...current, draft }))} onSend={send}
          sendLabel={t('chats.send')} max={MESSAGE_MAX_LENGTH} error={conversation.error === undefined ? undefined : sendError(conversation.error, t)}
          onEditLast={editLast} inputRef={composer} />
      )}>
      {history.loading && <DataLoadState />}
      {history.loadingOlder && <ChatOlder label={t('chats.loadingOlder')} />}
      {start}
      {items.length > 0 && (
        <ChatMessages label={t('chats.messages')}>
          {items.map((item, index) => {
            const previous = items[index - 1];
            const sentAt = new Date(item.sentAt);
            const newDay = !previous || !sameDay(new Date(previous.sentAt), sentAt);
            // yours still sending have no author of their own, so a run changes when the side does
            const first = newDay || previous.mine !== item.mine || (!item.mine && previous.author.number !== item.author.number)
              || sentAt.getTime() - new Date(previous.sentAt).getTime() > RUN_MS;
            const status = item.pending?.status;
            const own = item.mine ? item.message : undefined;
            const editor = own && editing?.id === own.id && (
              <ChatMessageEditor label={t('chats.editMessage')} value={editing.text} error={editing.error}
                onChange={text => setEditing({ ...editing, text, error: undefined })} onSave={() => saveEdit(own)} onCancel={stopEditing}
                hint={<><kbd>Esc</kbd> {t('chats.editCancel')} · <kbd>Enter</kbd> {t('chats.editSave')}</>} />
            );
            return (
              <div key={item.key}>
                {newDay && <ChatDay label={dayLabel(sentAt)} />}
                <ChatMessage mine={item.mine} text={item.text} first={first} author={item.author.name} avatarSrc={photoOf(item.author)}
                  time={timeFormat.format(sentAt)} dateTime={item.sentAt} status={status}
                  editedLabel={item.editedAt ? t('chats.edited') : undefined}
                  editedTitle={item.editedAt ? t('chats.editedAt', { when: editedFormat.format(new Date(item.editedAt)) }) : undefined}
                  statusLabel={status === 'failed' ? t('chats.notSent') : t('chats.sending')}
                  retryLabel={t('chats.retry')} discardLabel={t('chats.discard')}
                  onRetry={item.pending && (() => retry(item.pending!))} onDiscard={item.pending && (() => discard(item.pending!))}
                  toolsLabel={t('chats.messageActions')} editLabel={t('chats.edit')} deleteLabel={t('chats.delete')}
                  onEdit={own && (() => startEditing(own))} onDelete={own && (shift => askToDelete(own, shift))} editor={editor || undefined} />
              </div>
            );
          })}
        </ChatMessages>
      )}
      <ConfirmDialog open={deleting !== null} onClose={() => setDeleting(null)} tone="danger"
        title={t('chats.deleteTitle')} confirmLabel={t('chats.delete')}
        description={deleting && (
          <>
            {t('chats.deleteBody')}
            <span className="sm-chat__preview">{deleting.text}</span>
          </>
        )}
        onConfirm={() => {
          const message = deleting;
          setDeleting(null);
          if (message) return deleteForGood(message);
        }} />
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
