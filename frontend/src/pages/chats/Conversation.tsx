import type { TFunction } from 'i18next';
import { ArrowLeft, Ban, Clock, Ellipsis, Search } from 'lucide-react';
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { blockUser, unblockUser } from '../../api/blocks';
import { deleteMessage, editMessage, getDirectChat, reactToMessage, removeReaction, sendDirectMessage, sendFiles, sendGif, sendMessage, sendSticker } from '../../api/chats';
import type { GifData, GiphyProblem } from '../../api/giphy';
import { isBackendError } from '../../api/errors';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { useChatEvents, useChatListEvents, useChatSocketReconnect } from '../../hooks/useChatEvents';
import { typingLine, useTyping } from './useTyping';
import { Banner, buttonClass, ChatComposer, ChatGif, ChatGifButton, ChatGifPicker, ChatDay, ChatHead, ChatJump, ChatMessage, ChatMessageEditor, ChatMessages, ChatOlder, ChatEmojiPicker, ChatReplyBar, ChatTyping, ChatStart, ChatThread, ConfirmDialog, Dialog, Button, Menu, EMOJI_CATEGORIES, flashMessage, IconButton, SearchField, useToast, type ChatQuote, type ChatReaction, type MenuEntry, ChatStickerButton, ChatStickerPicker, ANIMATED_STICKERS, orderedPacks, packInSeason, StickerArt, type ChatGifResults, type ChatStickerPack } from '../../studymatch-ui';
import type { Sticker } from '../../types/enums';
import { MESSAGE_MAX_LENGTH, type ChatContactData, type DirectChatData, type MessageData, type MessageDeletedData, type MessageQuoteData, type ReactionData } from '../../types/chat';
import { ACCEPT, fileProblem, MAX_FILE_MB, MAX_FILES } from './attachmentFiles';
import { ComposerFiles, MessageFiles, PendingFiles, type OpenImage } from './AttachmentViews';
import { ChatSearch } from './ChatSearch';
import { newMessageKey, useConversation, type ThreadMessage } from './conversations';
import { lineOf } from './messageText';
import { recentStickers, rememberSticker } from './recentStickers';
import { useChatHistory } from './useChatHistory';
import { useGifSearch, useMessageGifs, type GifSearch } from './useGifs';

// a DM with one person, or a group chat by its id (#227)
export type ConversationChat =
  | { kind: 'direct'; chat: DirectChatData }
  | { kind: 'group'; chatId: number; name: string; pictureSrc?: string; members: ChatContactData[] };

export interface ConversationProps {
  chat: ConversationChat;
  // your number, to tell your own messages apart
  me: number;
  // where someone's profile is, for the people who have one you can open
  profilePath?: (contact: ChatContactData) => string | undefined;
  // who has the app open, for the green dots (#223)
  online?: ReadonlySet<number>;
  // the people you blocked: their messages are hidden behind a click (GR9, #225)
  blocked?: ReadonlySet<number>;
  // after you block or unblock someone here
  onBlocksChanged?: () => void;
  // the head's menu, like a group chat's Add members (#229)
  menuItems?: MenuEntry[];
}

// what double tapping a message reacts with
const HEART = '❤️';
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
  // a reply: what it quotes, null when the original was deleted
  reply: boolean;
  replyTo: MessageQuoteData | null;
  sticker?: Sticker | null;
  gifId?: string | null;
  // the message as the backend has it, for editing and deleting it
  message?: MessageData;
  pending?: ThreadMessage;
}

const ignore = () => undefined;
const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString();
// only students have a photo
const photoOf = (author: ChatContactData) => author.role === 'STUDENT' ? `/api/students/${author.number}/pfp` : undefined;

// a DM or a group chat: its history, live messages while it's open, editing and deleting your own, and the message box
// (#203, #205, #207, #209, #227)
export function Conversation({ chat, me, profilePath, online, blocked, onBlocksChanged, menuItems }: ConversationProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  // the other person in a DM; a group chat has none
  const contact = chat.kind === 'direct' ? chat.chat.contact : null;
  const [conversation, update] = useConversation(contact ? `direct:${contact.number}` : `chat:${chat.kind === 'group' ? chat.chatId : ''}`);
  // a DM gets its id with its first message, yours or theirs: from then on its history loads and its live events are followed
  const [chatId, setChatId] = useState(chat.kind === 'direct' ? chat.chat.chatId : chat.chatId);
  const { history, loadOlder, loadNewer, jumpTo, toLatest, behind, add, replace, remove, applyReaction, markRead, catchUp } = useChatHistory(chatId);
  // what was sent, edited or deleted while the connection was down (#317)
  useChatSocketReconnect(() => { void catchUp(); });
  const toast = useToast();
  // a message you sent keeps the key it had while sending, so it doesn't jump when the backend answers.
  // Each key belongs to one message: its live event can take it before the answer, which then puts it right
  const claims = useRef<ReadonlyMap<number, string>>(new Map());
  const [keys, setKeys] = useState(claims.current);
  const claim = (next: Map<number, string>) => {
    claims.current = next;
    setKeys(next);
  };
  const [newBelow, setNewBelow] = useState(false);
  const scroll = useRef<HTMLDivElement>(null);
  const atBottom = useRef(true);
  // while older messages load, the distance from the bottom to keep, so what you're reading stays put
  const keepFromBottom = useRef<number | null>(null);
  const lastKey = useRef<string | undefined>(undefined);
  // the last place the screen went to (history.focus)
  const focused = useRef(0);
  const composer = useRef<{ focus: () => void }>(null);
  // the message being edited, with its new text and why saving it failed
  const [editing, setEditing] = useState<{ id: number; text: string; error?: string; saving?: boolean } | null>(null);
  // the box open now, for an edit that finishes after you've moved on
  const editingNow = useRef(editing);
  useEffect(() => { editingNow.current = editing; });
  // the message the emoji picker is open for, and the button it sits by
  const [picking, setPicking] = useState<{ message: MessageData; anchor: HTMLElement } | null>(null);
  const closePicker = useCallback(() => setPicking(null), []);
  // the search in the head and its results on the right (#221)
  const [searching, setSearching] = useState(false);
  const [askingToBlock, setAskingToBlock] = useState(false);
  // messages from someone you blocked that you clicked to see
  const [shown, setShown] = useState<ReadonlySet<number>>(new Set());
  // an image opened bigger (#211)
  const [viewing, setViewing] = useState<OpenImage | null>(null);
  const [query, setQuery] = useState('');
  const searchInput = useRef<HTMLInputElement>(null);
  useEffect(() => { if (searching) searchInput.current?.focus(); }, [searching]);
  const stopSearching = () => {
    setSearching(false);
    setQuery('');
  };
  // the message waiting for Delete to be confirmed
  const [deleting, setDeleting] = useState<MessageData | null>(null);

  const name = contact ? contact.name : chat.kind === 'group' ? chat.name : '';
  const members = chat.kind === 'group' ? chat.members : [];
  // how many of the others in a group chat have the app open (#223)
  const othersOnline = members.filter(member => member.number !== me && online?.has(member.number)).length;
  const memberLine = t('chats.memberCount', { count: members.length });
  const line = contact ? t('chats.contactLine', { role: t(`roles.${contact.role}`), number: contact.number })
    : othersOnline ? t('chats.membersOnline', { members: memberLine, count: othersOnline }) : memberLine;
  // a person is called by their first name, a group chat by its whole name
  const firstName = contact ? contact.name.split(' ')[0] : name;
  const avatarSrc = contact ? photoOf(contact) : chat.kind === 'group' ? chat.pictureSrc : undefined;
  const self: ChatContactData = { number: me, name: t('chats.you'), role: 'STUDENT' };
  const timeFormat = new Intl.DateTimeFormat(i18n.language, { hour: '2-digit', minute: '2-digit' });
  const dayFormat = new Intl.DateTimeFormat(i18n.language, { day: 'numeric', month: 'long', year: 'numeric' });
  const editedFormat = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeStyle: 'short' });
  const isMine = (author: ChatContactData) => author.number === me;

  const items: Item[] = [
    ...history.messages.map(message => ({
      key: keys.get(message.id) ?? `message-${message.id}`, text: message.text, sentAt: message.sentAt, author: message.author,
      mine: isMine(message.author), editedAt: message.editedAt, reply: message.reply, replyTo: message.replyTo, sticker: message.sticker, gifId: message.gifId, message,
    })),
    ...conversation.messages.filter(message => !claimed(keys).has(message.key)).map(message => ({
      key: message.key, text: message.text, sentAt: message.sentAt, author: self, mine: true,
      reply: message.replyTo !== undefined, replyTo: message.replyTo ?? null, sticker: message.sticker, gifId: message.gifId, pending: message,
    })),
  ];
  const nameOf = (author: ChatContactData) => isMine(author) ? t('chats.you') : author.name;

  // your live event, which can come before the answer to sending it: it takes the key of one still sending with
  // that text, unless the answer already gave it its own
  const ownEvent = (message: MessageData) => {
    if (!claims.current.has(message.id)) {
      update(current => {
        const key = unclaimed(current.messages, contentOf(message), claims.current);
        if (key) claim(new Map(claims.current).set(message.id, key));
        return current;
      });
    }
    add(message);
  };
  // the answer to sending: the message gets that key for good. If another one's event took it, that one gets back
  // the key it should have had, so the same text sent twice keeps two messages with a key each
  const answered = (key: string, message: MessageData) => {
    update(current => {
      const next = new Map(claims.current);
      const before = next.get(message.id);
      const taker = holder(next, key, message.id);
      next.set(message.id, key);
      if (taker !== undefined) {
        next.delete(taker);
        const other = before !== undefined && before !== key ? before : unclaimed(current.messages, contentOf(message), next, key);
        if (other) next.set(taker, other);
      }
      claim(next);
      return { ...current, messages: current.messages.filter(m => m.key !== key) };
    });
    add(message);
  };
  // sending failed: it shows Retry, unless a live event already showed it was sent
  const notSent = (key: string, error: unknown) => update(current => {
    const next = new Map(claims.current);
    const taker = holder(next, key);
    const sent = current.messages.find(m => m.key === key);
    if (taker !== undefined) {
      next.delete(taker);
      const other = sent === undefined ? undefined : unclaimed(current.messages, contentOf(sent), next, key);
      if (!other) return { ...current, messages: current.messages.filter(m => m.key !== key) };
      // the event was another one's with the same text: it takes that one's key, and this one failed
      claim(next.set(taker, other));
    }
    // the original is gone: Retry sends it as a plain message, the error under the box says why
    const gone = isBackendError(error) && error.code === 'REPLIED_MESSAGE_NOT_FOUND';
    return { ...current, error, messages: current.messages.map(m => m.key === key ? { ...m, status: 'failed', replyTo: gone ? undefined : m.replyTo } : m) };
  });

  // their first message creates the DM while it's open here. The event comes for every chat and doesn't say which kind,
  // so a message of theirs in a group you share would look the same: the DM itself says whether it exists now
  useChatListEvents(event => {
    const message = event.payload as Partial<MessageData> | null;
    if (!contact || chatId !== null || message?.author?.number !== contact.number) return;
    getDirectChat(contact.number).then(dm => { if (dm.chatId !== null) setChatId(id => id ?? dm.chatId); }, ignore);
  });

  const typing = useTyping(chatId, person => isMine(person));

  useChatEvents(chatId, event => {
    if (typing.onEvent(event)) return;
    if (event.type === 'MESSAGE_EDITED') return replace(event.payload as MessageData);
    if (event.type === 'MESSAGE_REACTED') return applyReaction(event.payload as ReactionData);
    if (event.type === 'MESSAGE_DELETED') {
      const { messageId } = event.payload as MessageDeletedData;
      remove(messageId);
      // deleted in another tab while you were editing it here
      setEditing(current => current?.id === messageId ? null : current);
      // the one you were answering is gone, so the reply would be refused
      if (conversation.replyTo?.id === messageId) update(current => ({ ...current, replyTo: undefined }));
      return;
    }
    if (event.type !== 'MESSAGE_CREATED') return;
    const message = event.payload as MessageData;
    if (isMine(message.author)) return ownEvent(message);
    // after a jump the latest aren't on screen: it waits for them, behind New messages
    if (behind()) return setNewBelow(true);
    add(message);
    if (!atBottom.current) setNewBelow(true);
    // you're looking at it, so it's read; otherwise once you come back to the tab
    markRead();
  });

  // older messages keep what you're reading in place; a new one at the bottom is followed when you're there or it's yours
  useLayoutEffect(() => {
    const box = scroll.current;
    if (!box) return;
    // going to a message (a quote, a search result), or back to the bottom
    const { focus } = history;
    if (focus && focus.seq !== focused.current && !history.loading) {
      focused.current = focus.seq;
      lastKey.current = items.at(-1)?.key;
      keepFromBottom.current = null;
      const row = focus.id === undefined ? null : document.getElementById(rowId(focus.id));
      if (row) {
        // only the messages scroll, to put it in the middle: scrollIntoView would move the page sideways on a phone
        box.scrollTop += row.getBoundingClientRect().top - box.getBoundingClientRect().top - (box.clientHeight - row.offsetHeight) / 2;
        flashMessage(row);
      } else {
        box.scrollTop = box.scrollHeight;
      }
      atBottom.current = !history.hasNewer && box.scrollHeight - box.scrollTop - box.clientHeight < EDGE_PX;
      return;
    }
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

  // something on screen grew after it was shown, like an image or a file card loading (#211): at the bottom, it stays there
  useEffect(() => {
    const box = scroll.current;
    if (!box || typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(() => {
      if (atBottom.current && keepFromBottom.current === null && !history.hasNewer) box.scrollTop = box.scrollHeight;
    });
    Array.from(box.children).forEach(child => observer.observe(child));
    return () => observer.disconnect();
  });

  function older(box: HTMLDivElement) {
    const distance = box.scrollHeight - box.scrollTop;
    if (loadOlder()) keepFromBottom.current = distance;
  }

  const onScroll = () => {
    const box = scroll.current;
    if (!box) return;
    atBottom.current = box.scrollHeight - box.scrollTop - box.clientHeight < EDGE_PX;
    if (atBottom.current && newBelow && !history.hasNewer) setNewBelow(false);
    if (box.scrollTop < EDGE_PX) older(box);
    // after a jump, the ones after it as you scroll down
    if (box.scrollHeight - box.scrollTop - box.clientHeight < EDGE_PX) loadNewer();
  };

  const jump = () => {
    setNewBelow(false);
    // the latest aren't loaded after a jump: they come back, at the bottom
    if (history.hasNewer) return toLatest();
    if (scroll.current) scroll.current.scrollTop = scroll.current.scrollHeight;
    atBottom.current = true;
  };

  // go to the original of a reply, loading it if it's not on screen
  const goTo = (id: number) => jumpTo(id).catch(error => toast.danger(isBackendError(error) && error.code === 'NOT_FOUND' ? t('chats.originalGone') : t('common.unexpectedError')));

  // how much of a message's files went up, on its cards
  const progressed = (key: string, progress: number) =>
    update(current => ({ ...current, messages: current.messages.map(m => m.key === key ? { ...m, progress } : m) }));
  const deliver = (key: string, text: string, replyTo?: MessageQuoteData, files?: File[], sticker?: Sticker, gifId?: string) => {
    const to = contact ? { number: contact.number } : { chatId: chatId! };
    (sticker ? sendSticker(to, sticker, replyTo?.id)
      : gifId ? sendGif(to, gifId, replyTo?.id)
      : files?.length ? sendFiles(to, text, files, replyTo?.id, progress => progressed(key, progress))
      : contact ? sendDirectMessage(contact.number, text, replyTo?.id) : sendMessage(chatId!, text, replyTo?.id)).then(
      message => {
        answered(key, message);
        setChatId(id => id ?? message.chatId);
      },
      error => notSent(key, error),
    );
  };
  const send = () => {
    const text = conversation.draft.trim();
    const key = newMessageKey();
    const { replyTo } = conversation;
    const files = conversation.files?.length ? conversation.files : undefined;
    typing.sent();
    update(current => ({
      draft: '',
      error: undefined,
      replyTo: undefined,
      files: undefined,
      messages: [...current.messages, { key, text, sentAt: new Date().toISOString(), status: 'sending', replyTo, files, progress: files && 0 }],
    }));
    // what you send goes at the end of the chat, so the latest come back if you'd jumped to an old message
    toLatest();
    deliver(key, text, replyTo, files);
  };
  // a sticker goes on its own, straight away, answering the message being replied to (#215)
  const [stickers, setStickers] = useState<HTMLElement | null>(null);
  const closeStickers = useCallback(() => setStickers(null), []);
  const sendStickerNow = (sticker: Sticker) => {
    rememberSticker(sticker);
    const key = newMessageKey();
    const { replyTo } = conversation;
    setStickers(null);
    composer.current?.focus();
    update(current => ({
      ...current,
      error: undefined,
      replyTo: undefined,
      messages: [...current.messages, { key, text: '', sticker, sentAt: new Date().toISOString(), status: 'sending', replyTo }],
    }));
    toLatest();
    deliver(key, '', replyTo, undefined, sticker);
  };
  const retry = (message: ThreadMessage) => {
    update(current => ({ ...current, error: undefined, messages: current.messages.map(m => m.key === message.key
      ? { ...m, status: 'sending', progress: m.files && 0 } : m) }));
    deliver(message.key, message.text, message.replyTo, message.files, message.sticker, message.gifId);
  };
  // a GIF goes on its own straight away too (#213)
  const [gifPanel, setGifPanel] = useState<HTMLElement | null>(null);
  const closeGifs = useCallback(() => setGifPanel(null), []);
  const { gifs, remember: rememberGif } = useMessageGifs(items.flatMap(item => item.gifId ? [item.gifId] : []));
  const sendGifNow = (gif: GifData) => {
    rememberGif(gif);
    const key = newMessageKey();
    const { replyTo } = conversation;
    setGifPanel(null);
    composer.current?.focus();
    update(current => ({
      ...current,
      error: undefined,
      replyTo: undefined,
      messages: [...current.messages, { key, text: '', gifId: gif.id, sentAt: new Date().toISOString(), status: 'sending', replyTo }],
    }));
    toLatest();
    deliver(key, '', replyTo, undefined, undefined, gif.id);
  };

  // files picked, dropped or pasted: they show as cards above the box, the ones that can't be sent say why (#211)
  const addFiles = (files: File[]) => {
    if (!files.length) return;
    update(current => ({ ...current, error: undefined, files: [...(current.files ?? []), ...files] }));
    composer.current?.focus();
  };
  const removeFile = (index: number) =>
    update(current => ({ ...current, files: current.files?.filter((_, at) => at !== index) }));
  const added = conversation.files ?? [];
  const filesInvalid = added.some((file, index) => fileProblem(file, index, t) !== undefined);

  // your reaction to a message, if you have one
  const yoursOn = (id: number) => history.reactions.find(reaction => reaction.messageId === id && isMine(reaction.user));
  const reactionFailed = (error: unknown) => {
    toast.danger(isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError'));
  };
  // one each: another emoji replaces yours
  const react = (message: MessageData, emoji: string) => {
    if (yoursOn(message.id)?.emoji === emoji) return;
    reactToMessage(message.chatId, message.id, emoji).then(applyReaction, reactionFailed);
  };
  const unreact = (reaction: ReactionData) => removeReaction(reaction.chatId, reaction.messageId).then(
    () => applyReaction({ ...reaction, emoji: null }), reactionFailed);
  // a pill: yours takes it off, another one makes it yours
  const pickPill = (message: MessageData, emoji: string) => {
    const yours = yoursOn(message.id);
    if (yours?.emoji === emoji) return unreact(yours);
    react(message, emoji);
  };
  const list = new Intl.ListFormat(i18n.language, { type: 'conjunction' });
  // the same emoji from several people is one pill with the count, in the order they first appeared
  const pillsOf = (id: number): ChatReaction[] => {
    const byEmoji = new Map<string, ReactionData[]>();
    history.reactions.filter(reaction => reaction.messageId === id && reaction.emoji)
      .forEach(reaction => byEmoji.set(reaction.emoji!, [...(byEmoji.get(reaction.emoji!) ?? []), reaction]));
    return [...byEmoji].map(([emoji, people]) => {
      const mine = people.some(person => isMine(person.user));
      // you first, then the others by name
      const who = list.format([...(mine ? [t('chats.you')] : []), ...people.filter(person => !isMine(person.user)).map(person => person.user.name)]);
      return { emoji, count: people.length, mine, title: who, label: t(mine ? 'chats.reactionYours' : 'chats.reaction', { emoji, count: people.length, who }) };
    });
  };
  const picker = picking && (
    <ChatEmojiPicker anchor={picking.anchor} label={t('chats.pickReaction')} searchLabel={t('chats.searchEmoji')} loadingLabel={t('chats.loadingEmoji')}
      categoryNames={Object.fromEntries(EMOJI_CATEGORIES.map(category => [category, t(`chats.emojiGroups.${category}`)])) as Record<(typeof EMOJI_CATEGORIES)[number], string>}
      onClose={closePicker} onPick={emoji => {
        const { message } = picking;
        setPicking(null);
        picking.anchor.focus();
        react(message, emoji);
      }} />
  );

  // answering a message: it shows above the message box until it's sent, or Esc or the X stops it
  const startReply = (message: MessageData) => {
    update(current => ({ ...current, replyTo: { id: message.id, author: message.author, text: message.text, gifId: message.gifId, sticker: message.sticker, attachments: message.attachments } }));
    composer.current?.focus();
  };
  const stopReply = () => {
    if (!conversation.replyTo) return false;
    update(current => ({ ...current, replyTo: undefined }));
    composer.current?.focus();
    return true;
  };
  const isOnline = (person: ChatContactData) => online?.has(person.number) ?? false;

  const replyBar = conversation.replyTo && (
    <ChatReplyBar title={isMine(conversation.replyTo.author) ? t('chats.replyingToYourself')
      : <Trans i18nKey="chats.replyingTo" values={{ name: conversation.replyTo.author.name }} components={{ b: <b /> }} />}
      preview={lineOf(conversation.replyTo, t)} cancelLabel={t('chats.cancelReply')} onCancel={stopReply} />
  );
  // what a reply shows above it: one line of the original, or that it was deleted
  const quoteOf = (item: Item): ChatQuote | undefined => {
    if (!item.reply) return undefined;
    const original = item.replyTo;
    if (!original) return { text: t('chats.originalDeleted'), gone: true };
    return { author: nameOf(original.author), text: lineOf(original, t), label: t('chats.goToOriginal', { name: nameOf(original.author) }), onClick: () => goTo(original.id) };
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
    // a sticker or a GIF has no text to change (GR10)
    const last = items.findLast(item => item.mine && item.message && !item.message.sticker && !item.message.gifId);
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
        // back to the message box, unless you're editing another message by now
        if (!isThis(editingNow.current)) return;
        setEditing(null);
        composer.current?.focus();
      },
      error => setEditing(current => isThis(current) ? { ...current!, saving: false, error: sendError(error, t) } : current),
    );
  };
  const deleteForGood = (message: MessageData) => deleteMessage(message.chatId, message.id).then(
    () => remove(message.id),
    error => { toast.danger(isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError')); },
  );
  const askToDelete = (message: MessageData, shift: boolean) => shift ? deleteForGood(message) : setDeleting(message);

  // a DM with someone you blocked: no history and no message box until you unblock them
  const youBlocked = chat.kind === 'direct' && chat.chat.blocked;
  // they aren't told either way (GR9). Blocking takes the DM off your list, so the list opens
  const block = (person: ChatContactData) => blockUser(person.number).then(
    () => { toast.success(t('chats.blockedToast', { name: person.name })); onBlocksChanged?.(); navigate('/chats'); },
    error => { toast.danger(isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError')); },
  );
  const unblock = (person: ChatContactData) => unblockUser(person.number).then(
    () => { toast.success(t('chats.unblockedToast', { name: person.name })); onBlocksChanged?.(); },
    error => { toast.danger(isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError')); },
  );
  // only a DM has it, someone in a group chat is blocked from their DM
  // what the page adds (a group chat's Add members, #229), then Block in a DM
  const menuEntries: MenuEntry[] = [
    ...(menuItems ?? []),
    ...(contact && !youBlocked ? [{ label: t('chats.block', { name: firstName }), icon: Ban, danger: true, onSelect: () => setAskingToBlock(true) }] : []),
  ];
  const menu = menuEntries.length > 0 && (
    <Menu label={t('chats.chatOptions')} trigger={<IconButton icon={Ellipsis} label={t('chats.chatOptions')} variant="ghost" />} items={menuEntries} />
  );

  const head = (
    <ChatHead name={name} avatarSrc={avatarSrc} subtitle={contact && isOnline(contact) ? t('chats.online') : line} online={!!contact && isOnline(contact)}
      backLabel={t('chats.back')} onBack={() => navigate('/chats')} group={!contact}
      profileHref={contact ? profilePath?.(contact) : undefined} linkAs={Link} profileLabel={t('chats.openProfile', { name })}
      actions={<>{chatId !== null && (searching
        ? <SearchField ref={searchInput} size="sm" className="sm-chat__head-search" value={query} onValueChange={setQuery}
          label={t('chats.searchChat')} placeholder={t('chats.searchChat')}
          // the first Esc empties it, the next one closes the search
          onKeyDown={event => { if (event.key === 'Escape' && !query) stopSearching(); }} />
        : <IconButton icon={Search} label={t('chats.searchChat')} variant="ghost" onClick={() => setSearching(true)} />)}{menu}</>} />
  );

  if (history.error !== undefined) {
    return (
      <ChatThread label={t('chats.with', { name })} head={head}>
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

  const startText = () => {
    if (!contact) return t(items.length ? 'chats.startOfGroupChat' : 'chats.groupSayHi', { name });
    return items.length ? t('chats.startOfConversation', { name: firstName }) : t('chats.sayHi', { line, name: firstName });
  };
  // the top of the conversation once its first message is on screen, or a hint to say hi when there's none
  const start = history.loading || history.hasMore ? null
    : <ChatStart name={name} avatarSrc={avatarSrc} group={!contact}>{youBlocked ? t('chats.blockedStart', { line }) : startText()}</ChatStart>;

  return (
    <ChatThread label={t('chats.with', { name })} scrollRef={scroll} onScroll={onScroll} head={head}
      // after a jump the latest aren't on screen, so the button stays until they're back
      overlay={(newBelow || history.hasNewer) && <ChatJump label={t(newBelow ? 'chats.newMessages' : 'chats.backToLatest')} onClick={jump} />}
      drop={youBlocked ? undefined : { title: t('chats.dropTitle', { name: firstName }), hint: t('chats.dropHint', { max: MAX_FILES, size: MAX_FILE_MB }), onDrop: addFiles }}
      side={searching && chatId !== null && <ChatSearch chatId={chatId} query={query} onClose={stopSearching} onJump={jumpTo} nameOf={nameOf} />}
      composer={youBlocked ? (
        <div className="sm-chat__blocked-bar" role="status">
          {t('chats.youBlocked', { name: firstName })}
          <Button size="sm" variant="secondary" onClick={() => { if (contact) void unblock(contact); }}>{t('chats.unblock')}</Button>
        </div>
      ) : (
        <ChatComposer label={t('chats.messageLabel', { name })} placeholder={t('chats.messageLabel', { name: firstName })}
          value={conversation.draft} onChange={draft => { update(current => ({ ...current, draft })); if (draft.trim()) typing.typed(); }} onSend={send}
          sendLabel={t('chats.send')} max={MESSAGE_MAX_LENGTH} error={conversation.error === undefined ? undefined : sendError(conversation.error, t)}
          onEditLast={editLast} inputRef={composer} above={<><ChatTyping text={typingLine(typing.typing, t)} />{replyBar}</>} onEscape={stopReply}
          onAddFiles={addFiles} addFilesLabel={t('chats.addFiles')} accept={ACCEPT}
          files={added.length > 0 && <ComposerFiles files={added} onRemove={removeFile} />} filesReady={added.length > 0} filesInvalid={filesInvalid}
          tools={<>
            <ChatStickerButton label={t('chats.stickers')} open={stickers !== null} onOpen={anchor => setStickers(current => current ? null : anchor)} />
            <ChatGifButton label={t('chats.gifs.open')} open={gifPanel !== null} onOpen={anchor => setGifPanel(current => current ? null : anchor)} />
          </>} />
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
            // a reply starts a run of its own, so its quote doesn't sit between someone's messages
            const first = newDay || item.reply || previous.mine !== item.mine || (!item.mine && previous.author.number !== item.author.number)
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
                <ChatMessage id={item.message && rowId(item.message.id)} mine={item.mine}
                  hidden={item.message && !item.mine && blocked?.has(item.author.number) && !shown.has(item.message.id)
                    ? { label: t('chats.fromBlocked'), onShow: () => setShown(current => new Set(current).add(item.message!.id)) } : undefined} text={item.text} quote={quoteOf(item)} sticker={item.sticker ? <StickerArt id={item.sticker} animated={ANIMATED_STICKERS.has(item.sticker)} className="sm-sticker--pop" /> : undefined}
                  gif={item.gifId ? <ChatGif gif={gifs.get(item.gifId)} unavailableLabel={t('chats.gifs.unavailable')} /> : undefined} first={first} author={item.author.name} online={isOnline(item.author)} avatarSrc={photoOf(item.author)}
                  time={timeFormat.format(sentAt)} dateTime={item.sentAt} status={status}
                  editedLabel={item.editedAt ? t('chats.edited') : undefined}
                  editedTitle={item.editedAt ? t('chats.editedAt', { when: editedFormat.format(new Date(item.editedAt)) }) : undefined}
                  statusLabel={status === 'failed' ? t('chats.notSent') : t('chats.sending')}
                  retryLabel={t('chats.retry')} discardLabel={t('chats.discard')}
                  onRetry={item.pending && (() => retry(item.pending!))} onDiscard={item.pending && (() => discard(item.pending!))}
                  toolsLabel={t('chats.messageActions')} editLabel={t('chats.edit')} deleteLabel={t('chats.delete')}
                  replyLabel={t('chats.reply')} onReply={item.message && (() => startReply(item.message!))}
                  reactions={item.message && pillsOf(item.message.id)} onReaction={emoji => item.message && pickPill(item.message, emoji)}
                  reactLabel={t('chats.addReaction')} picking={picking !== null && picking.message.id === item.message?.id}
                  onAddReaction={item.message && (anchor => setPicking(current => current?.message.id === item.message!.id ? null : { message: item.message!, anchor }))}
                  onDoubleTap={item.message && (() => react(item.message!, HEART))}
                  onEdit={own && !own.sticker && !own.gifId ? () => startEditing(own) : undefined} onDelete={own && (shift => askToDelete(own, shift))} editor={editor || undefined}
                  files={item.message?.attachments?.length
                    ? <MessageFiles chatId={item.message.chatId} messageId={item.message.id} attachments={item.message.attachments} onOpen={setViewing} />
                    : item.pending?.files?.length ? <PendingFiles files={item.pending.files} progress={item.pending.status === 'sending' ? item.pending.progress : undefined} /> : undefined} />
              </div>
            );
          })}
        </ChatMessages>
      )}
      {history.loadingNewer && <ChatOlder label={t('chats.loadingNewer')} />}
      {picker}
      <Dialog open={viewing !== null} onClose={() => setViewing(null)} title={viewing?.name ?? ''} size="lg">
        {viewing && <img className="sm-msg__lightbox" src={viewing.src} alt={viewing.name} />}
      </Dialog>
      {stickers && (
        <ChatStickerPicker anchor={stickers} label={t('chats.pickSticker')} searchLabel={t('chats.searchStickers')} hint={t('chats.stickerKeys')}
          inSeasonLabel={t('chats.inSeason')} animatedLabel={t('chats.animated')} emptyLabel={query => t('chats.noStickers', { query })}
          onClose={closeStickers} onPick={sendStickerNow} packs={stickerPacks(t)} />
      )}
      {gifPanel && (
        <GifPanel anchor={gifPanel} onPick={sendGifNow} onClose={closeGifs} />
      )}
      {contact && (
        <ConfirmDialog open={askingToBlock} onClose={() => setAskingToBlock(false)} tone="danger" icon={Ban}
          title={t('chats.blockTitle', { name: contact.name })} description={t('chats.blockBody')} confirmLabel={t('chats.blockConfirm')}
          onConfirm={async () => { setAskingToBlock(false); await block(contact); }} />
      )}
      <ConfirmDialog open={deleting !== null} onClose={() => setDeleting(null)} tone="danger"
        title={t('chats.deleteTitle')} confirmLabel={t('chats.delete')}
        description={deleting && (
          <>
            {t('chats.deleteBody')}
            <span className="sm-chat__preview">{lineOf(deleting, t)}</span>
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

// the picker's packs: the ones you sent last, then the one in season, then the rest
function stickerPacks(t: TFunction): ChatStickerPack[] {
  const item = (id: Sticker) => ({ id, name: t(`stickers.${id}`), animated: ANIMATED_STICKERS.has(id) });
  const recent = recentStickers();
  const season = packInSeason(new Date());
  return [
    ...(recent.length ? [{ id: 'recent', name: t('chats.stickerPacks.recent'), icon: <Clock size={20} aria-hidden />, stickers: recent.map(item) }] : []),
    ...orderedPacks(new Date()).map(pack => ({ id: pack.id, name: t(`chats.stickerPacks.${pack.id}`), inSeason: pack.id === season, stickers: pack.stickers.map(item) })),
  ];
}

// a message's row, to go to it
const rowId = (id: number) => `message-${id}`;

// the keys messages already have
const claimed = (claims: ReadonlyMap<number, string>) => new Set(claims.values());
// the message holding that key, other than `except`
const holder = (claims: ReadonlyMap<number, string>, key: string, except?: number) =>
  [...claims].find(([id, k]) => k === key && id !== except)?.[0];
// what tells two messages apart while they're sending: the text, or the sticker (which has none)
const contentOf = (message: { text: string; sticker?: Sticker | null; gifId?: string | null }) =>
  message.sticker ? `sticker:${message.sticker}` : message.gifId ? `gif:${message.gifId}` : `text:${message.text}`;

// what the GIF panel shows for a search: GIPHY's problems have their own text, they aren't our error codes
const GIPHY_PROBLEMS: Record<GiphyProblem, string> = { noKey: 'chats.gifs.noKey', limit: 'chats.gifs.limit', down: 'chats.gifs.down' };
function gifResults(search: GifSearch, retry: () => void, t: TFunction): ChatGifResults {
  if (search.status === 'loading') return search;
  if (search.status === 'failed') return { status: 'failed', message: t(GIPHY_PROBLEMS[search.problem]), retryLabel: t('chats.gifs.retry'), onRetry: retry };
  return search.gifs.length ? { status: 'ready', gifs: search.gifs } : { status: 'empty', message: t('chats.gifs.none') };
}

// the GIF panel, mounted only while it's open, so its search starts over each time
function GifPanel({ anchor, onPick, onClose }: { anchor: HTMLElement; onPick: (gif: GifData) => void; onClose: () => void }) {
  const { t } = useTranslation();
  const search = useGifSearch();
  return (
    <ChatGifPicker anchor={anchor} label={t('chats.gifs.pick')} searchLabel={t('chats.gifs.search')} query={search.query} onQueryChange={search.setQuery}
      results={gifResults(search.result, search.retry, t)} poweredByLabel={t('chats.gifs.poweredBy')} onPick={onPick} onClose={onClose} />
  );
}
// the oldest one still sending with that content that no message holds yet
const unclaimed = (messages: ThreadMessage[], content: string, claims: ReadonlyMap<number, string>, except?: string) => {
  const taken = claimed(claims);
  return messages.find(m => m.status === 'sending' && contentOf(m) === content && m.key !== except && !taken.has(m.key))?.key;
};

// the text's own problem (empty, too long) when the backend names it, otherwise the error's code
function sendError(error: unknown, t: TFunction) {
  if (!isBackendError(error)) return t('common.unexpectedError');
  const field = error.fieldErrors?.find(f => f.field === 'text');
  if (field) return t(`validation.${field.code}`, { ...field.params, defaultValue: t('validation.INVALID') });
  return t(`errors.${error.code}`, error.params ?? {});
}
