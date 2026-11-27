import { ArrowDown, ArrowUp, ChevronLeft, CircleAlert, CornerUpLeft, Pencil, Reply, SmilePlus, Trash2, X } from 'lucide-react';
import { useEffect, useId, useImperativeHandle, useLayoutEffect, useRef, type ElementType, type MouseEvent, type PointerEvent, type ReactNode, type Ref, type UIEventHandler } from 'react';
import { Avatar } from '../../components/avatar/Avatar';
import { Button, IconButton } from '../../components/button/Button';
import { Spinner } from '../../components/progress/Progress';
import './chat.css';

export interface ChatLayoutProps {
  list: ReactNode;
  thread: ReactNode;
  // on a phone only one shows: the list, or the open conversation
  view: 'list' | 'thread';
  label: string;
}

// the Chats page: the chat list on the left, the open conversation on the right
export function ChatLayout({ list, thread, view, label }: ChatLayoutProps) {
  return <div className="sm-chat" data-view={view} aria-label={label} role="region">{list}{thread}</div>;
}

export interface ChatListPaneProps {
  title: string;
  actions?: ReactNode;
  children?: ReactNode;
}

export function ChatListPane({ title, actions, children }: ChatListPaneProps) {
  return (
    <section className="sm-chat__list" aria-label={title}>
      {/* the top bar already shows the page's name; the heading stays for screen readers */}
      <h1 className="sm-visually-hidden">{title}</h1>
      {actions && <div className="sm-chat__list-head"><div className="sm-chat__new">{actions}</div></div>}
      <div className="sm-chat__rows">{children}</div>
    </section>
  );
}

export interface ChatThreadProps {
  label: string;
  head?: ReactNode;
  // the message box, under the messages
  composer?: ReactNode;
  scrollRef?: Ref<HTMLDivElement>;
  onScroll?: UIEventHandler<HTMLDivElement>;
  // floats over the messages, like the New messages button
  overlay?: ReactNode;
  // a panel on the right of the messages, like the search results; it stops above the message box
  side?: ReactNode;
  children?: ReactNode;
}

export function ChatThread({ label, head, composer, scrollRef, onScroll, overlay, side, children }: ChatThreadProps) {
  return (
    <section className={side ? 'sm-chat__thread sm-chat__thread--side' : 'sm-chat__thread'} aria-label={label}>
      {head}
      <div className="sm-chat__scroll" ref={scrollRef} onScroll={onScroll}>{children}</div>
      {side}
      {overlay}
      {composer}
    </section>
  );
}

// where a new day starts among the messages
export function ChatDay({ label }: { label: string }) {
  return <div className="sm-chat__day"><span className="sm-eyebrow">{label}</span></div>;
}

// at the top while older messages load
export function ChatOlder({ label }: { label: string }) {
  return <div className="sm-chat__older"><Spinner size={16} label={label} />{label}</div>;
}

// shown instead of jumping down when a message arrives while you're reading older ones
export function ChatJump({ label, onClick }: { label: string; onClick: () => void }) {
  return <Button className="sm-chat__jump" size="sm" variant="black" icon={ArrowDown} onClick={onClick}>{label}</Button>;
}

export interface ChatHeadProps {
  name: string;
  // the chat's picture; initials when there's none
  avatarSrc?: string;
  subtitle?: string;
  backLabel: string;
  onBack: () => void;
  // their profile, when they have one: the picture and name link to it
  profileHref?: string;
  profileLabel?: string;
  // the router's link, like the app shell's
  linkAs?: ElementType;
  // on the right, like the search
  actions?: ReactNode;
}

// who the conversation is with; the back button only shows on a phone
export function ChatHead({ name, avatarSrc, subtitle, backLabel, onBack, profileHref, profileLabel, linkAs, actions }: ChatHeadProps) {
  const LinkAs = linkAs ?? 'a';
  const who = (
    <>
      <Avatar name={name} src={avatarSrc} size={40} />
      <div className="sm-chat__head-text">
        <h2 className="sm-chat__head-name">{name}</h2>
        {subtitle && <div className="sm-chat__head-sub">{subtitle}</div>}
      </div>
    </>
  );
  return (
    <header className="sm-chat__head">
      <IconButton className="sm-chat__back" icon={ChevronLeft} label={backLabel} variant="ghost" onClick={onBack} />
      {profileHref
        ? <LinkAs href={profileHref} to={linkAs ? profileHref : undefined} className="sm-chat__who sm-chat__who--link" aria-label={profileLabel}>{who}</LinkAs>
        : <div className="sm-chat__who">{who}</div>}
      {actions && <div className="sm-chat__head-actions">{actions}</div>}
    </header>
  );
}

export interface ChatSheetProps {
  label: string;
  title: ReactNode;
  closeLabel: string;
  onClose: () => void;
  children: ReactNode;
}

// a panel on the right of the conversation, under its head, like the search results
export function ChatSheet({ label, title, closeLabel, onClose, children }: ChatSheetProps) {
  return (
    <aside className="sm-chat__sheet" aria-label={label}>
      <div className="sm-chat__sheet-head">
        <h3>{title}</h3>
        <IconButton icon={X} label={closeLabel} variant="ghost" onClick={onClose} />
      </div>
      <div className="sm-chat__sheet-body">{children}</div>
    </aside>
  );
}

export interface ChatResultProps {
  author: string;
  time: string;
  dateTime: string;
  // the text, with what was searched for in <mark>
  children: ReactNode;
  onClick: () => void;
}

// a search result: who, when, and the words that matched; clicking it goes to the message
export function ChatResult({ author, time, dateTime, children, onClick }: ChatResultProps) {
  return (
    <li>
      <button type="button" className="sm-chat__result" onClick={onClick}>
        <span className="sm-chat__result-meta"><b>{author}</b><time dateTime={dateTime}>{time}</time></span>
        <span className="sm-chat__result-text">{children}</span>
      </button>
    </li>
  );
}

export interface ChatStartProps {
  name: string;
  avatarSrc?: string;
  children?: ReactNode;
}

// the top of a conversation: who it's with, and a word on how to begin
export function ChatStart({ name, avatarSrc, children }: ChatStartProps) {
  return (
    <div className="sm-chat__start">
      <Avatar name={name} src={avatarSrc} size={64} />
      <h3>{name}</h3>
      {children && <p>{children}</p>}
    </div>
  );
}

export interface ChatPersonProps {
  name: string;
  subtitle: string;
  tag?: string;
  onPick: () => void;
}

// someone to message, a row of the New message search
export function ChatPerson({ name, subtitle, tag, onPick }: ChatPersonProps) {
  return (
    <li>
      <button type="button" className="sm-person sm-person--pick" onClick={onPick}>
        <Avatar name={name} size={38} />
        <span className="sm-person__text">
          <span className="sm-person__name">{name}</span>
          <span className="sm-person__sub">{subtitle}</span>
        </span>
        {tag && <span className="sm-person__tag">{tag}</span>}
      </button>
    </li>
  );
}

export function ChatPeople({ label, children }: { label: string; children: ReactNode }) {
  return <ul className="sm-chat__people" aria-label={label}>{children}</ul>;
}

// the chat list (#201), its rows newest first
export function ChatRows({ label, children }: { label: string; children: ReactNode }) {
  return <ul className="sm-chat__row-list" aria-label={label}>{children}</ul>;
}

export interface ChatRowProps {
  name: string;
  avatarSrc?: string;
  // a group chat's picture is square, a person's round
  group?: boolean;
  // a line of the latest message, like "You: on my way"
  preview: string;
  time?: string;
  dateTime?: string;
  unread: number;
  // read out with the count, like "3 unread"
  unreadLabel?: string;
  href: string;
  // the chat that's open
  current?: boolean;
  // the router's link, like the app shell's
  linkAs?: ElementType;
}

// one chat in the list: picture, name, the latest message, when, and how many you haven't read
export function ChatRow({ name, avatarSrc, group, preview, time, dateTime, unread, unreadLabel, href, current, linkAs }: ChatRowProps) {
  const LinkAs = linkAs ?? 'a';
  return (
    <li>
      <LinkAs href={href} to={linkAs ? href : undefined} className={`sm-chat__row${unread ? ' sm-chat__row--unread' : ''}`}
        aria-current={current ? 'page' : undefined}>
        <Avatar name={name} src={avatarSrc} size={46} shape={group ? 'square' : 'circle'} />
        <span className="sm-chat__row-text">
          <span className="sm-chat__row-name">{name}</span>
          <span className="sm-chat__row-last">{preview}</span>
        </span>
        <span className="sm-chat__row-side">
          {time && <time className="sm-chat__row-time" dateTime={dateTime}>{time}</time>}
          {unread > 0 && (
            <span className="sm-chat__badge">
              <span aria-hidden>{unread}</span>
              <span className="sm-visually-hidden">{unreadLabel ?? unread}</span>
            </span>
          )}
        </span>
      </LinkAs>
    </li>
  );
}

export interface ChatMessagesProps {
  label: string;
  children: ReactNode;
}

// the messages of a conversation, read out as they arrive
export function ChatMessages({ label, children }: ChatMessagesProps) {
  return <div className="sm-chat__messages" role="log" aria-label={label}>{children}</div>;
}

// the original a reply quotes, above it: who wrote it and one line of it. Clicking it goes to the original.
// gone: the original was deleted, so there's only `text` saying so, in grey and not clickable
export interface ChatQuote {
  author?: string;
  text: string;
  gone?: boolean;
  // read out for the button, like "Go to Ana's message"
  label?: string;
  onClick?: () => void;
}

// one emoji under a message, with how many reacted with it. mine: yours is this one, so it's highlighted
export interface ChatReaction {
  emoji: string;
  count: number;
  mine: boolean;
  // who reacted, shown on hover, like "You and Ana"
  title: string;
  // read out for the button, like "❤️ 2, take yours off"
  label: string;
}

export interface ChatMessageProps {
  // on the message's row, for going to it
  id?: string;
  text: string;
  // when it was sent: shown as `time`, `dateTime` is the machine-readable one
  time: string;
  dateTime: string;
  mine?: boolean;
  // the first of a run by the same person, which shows who and when
  first?: boolean;
  author?: string;
  // the author's picture; their initials when there's none
  avatarSrc?: string;
  // "(edited)" after the text, with when in its tooltip
  editedLabel?: string;
  editedTitle?: string;
  // 'sending' until the backend confirms it, 'failed' with Retry and Discard
  status?: 'sending' | 'failed';
  statusLabel?: string;
  retryLabel?: string;
  discardLabel?: string;
  onRetry?: () => void;
  onDiscard?: () => void;
  // Edit and Delete, on hover or focus, for your own messages
  toolsLabel?: string;
  editLabel?: string;
  deleteLabel?: string;
  onEdit?: () => void;
  // shift: Delete was clicked holding Shift, which skips the question
  onDelete?: (shift: boolean) => void;
  // shown instead of the text while it's being edited, a ChatMessageEditor
  editor?: ReactNode;
  // what it replies to (#219)
  quote?: ChatQuote;
  // Reply, on hover or focus, for any message the backend has
  replyLabel?: string;
  onReply?: () => void;
  // the reactions under it (#217): clicking one picks that emoji, or takes yours off if it's yours
  reactions?: ChatReaction[];
  onReaction?: (emoji: string) => void;
  // Add reaction, in the tools and after the reactions; it gets the button, for the picker to sit by it
  reactLabel?: string;
  onAddReaction?: (button: HTMLElement) => void;
  // double clicking or double tapping the message, which reacts with a heart
  onDoubleTap?: () => void;
  // the picker is open for it, so its tools stay on screen
  picking?: boolean;
}

// two taps closer than this are a double tap
const DOUBLE_TAP_MS = 300;
// a double click on these is theirs, not the message's
const OWN_CLICKS = 'button, a, textarea, input';

export function ChatMessage({ id, text, time, dateTime, mine = false, first = true, author, avatarSrc, editedLabel, editedTitle, status, statusLabel, retryLabel, discardLabel, onRetry, onDiscard,
  toolsLabel, editLabel = '', deleteLabel = '', onEdit, onDelete, editor, quote, replyLabel = '', onReply,
  reactions, onReaction, reactLabel = '', onAddReaction, onDoubleTap, picking }: ChatMessageProps) {
  const cls = ['sm-msg', mine && 'sm-msg--mine', first && 'sm-msg--first', status === 'sending' && 'sm-msg--sending', picking && 'sm-msg--picking'].filter(Boolean).join(' ');
  // touch screens don't always send a double click, so two taps are counted here
  const lastTap = useRef(0);
  const touched = useRef(false);
  const tapped = (event: PointerEvent<HTMLDivElement>) => {
    touched.current = event.pointerType === 'touch';
    if (!touched.current || !onDoubleTap || (event.target as Element).closest(OWN_CLICKS)) return;
    if (event.timeStamp - lastTap.current < DOUBLE_TAP_MS) {
      lastTap.current = 0;
      onDoubleTap();
    } else {
      lastTap.current = event.timeStamp;
    }
  };
  const doubleClicked = (event: MouseEvent<HTMLDivElement>) => {
    if (touched.current || !onDoubleTap || (event.target as Element).closest(OWN_CLICKS)) return;
    onDoubleTap();
  };
  // a double click sends a heart, so its second press doesn't select the word under it too.
  // It's a shortcut for the mouse (React is the way with a keyboard), so it's listened to here and not on the line
  const line = useRef<HTMLDivElement>(null);
  const doubleTaps = !!onDoubleTap;
  const editing = !!editor;
  useEffect(() => {
    const element = line.current;
    if (!element || !doubleTaps) return undefined;
    const pressed = (event: globalThis.MouseEvent) => { if (event.detail > 1) event.preventDefault(); };
    element.addEventListener('mousedown', pressed);
    return () => element.removeEventListener('mousedown', pressed);
  }, [doubleTaps, editing]);
  return (
    <div className={cls} id={id}>
      {!mine && <div className="sm-msg__avatar">{first && author && <Avatar name={author} src={avatarSrc} size={36} />}</div>}
      <div className="sm-msg__main">
        {first && (
          <div className="sm-msg__meta">
            {!mine && author && <b>{author}</b>}
            <time dateTime={dateTime}>{time}</time>
          </div>
        )}
        {quote && <ChatMessageQuote {...quote} />}
        {editor ?? (
          <div className="sm-msg__line" ref={line} onPointerUp={tapped} onDoubleClick={doubleClicked}>
            <div className="sm-msg__bubble">{text}{editedLabel && <span className="sm-msg__edited" title={editedTitle}>{editedLabel}</span>}</div>
            {(onAddReaction || onReply || onEdit || onDelete) && (
              <div className="sm-msg__tools" role="toolbar" aria-label={toolsLabel}>
                {onAddReaction && <IconButton icon={SmilePlus} label={reactLabel} size="sm" variant="ghost" aria-haspopup="dialog" aria-expanded={picking ?? false}
                  onClick={(event: MouseEvent<HTMLButtonElement>) => onAddReaction(event.currentTarget)} />}
                {onReply && <IconButton icon={Reply} label={replyLabel} size="sm" variant="ghost" onClick={onReply} />}
                {onEdit && <IconButton icon={Pencil} label={editLabel} size="sm" variant="ghost" onClick={onEdit} />}
                {onDelete && <IconButton icon={Trash2} label={deleteLabel} size="sm" variant="ghost" className="sm-msg__delete"
                  onClick={(event: MouseEvent) => onDelete(event.shiftKey)} />}
              </div>
            )}
          </div>
        )}
        {reactions && reactions.length > 0 && (
          <div className="sm-reactions">
            {reactions.map(reaction => (
              <button key={reaction.emoji} type="button" className={`sm-reaction${reaction.mine ? ' sm-reaction--mine' : ''}`}
                aria-pressed={reaction.mine} aria-label={reaction.label} title={reaction.title} onClick={() => onReaction?.(reaction.emoji)}>
                <span aria-hidden>{reaction.emoji}</span><span aria-hidden>{reaction.count}</span>
              </button>
            ))}
            {onAddReaction && (
              <button type="button" className="sm-reaction sm-reaction--add" aria-label={reactLabel} title={reactLabel} aria-haspopup="dialog"
                onClick={event => onAddReaction(event.currentTarget)}>
                <SmilePlus size={14} aria-hidden />
              </button>
            )}
          </div>
        )}
        {status === 'sending' && <span className="sm-msg__status">{statusLabel}</span>}
        {status === 'failed' && (
          <div className="sm-msg__failed">
            <CircleAlert size={14} aria-hidden />{statusLabel}
            {onRetry && <button type="button" onClick={onRetry}>{retryLabel}</button>}
            {onDiscard && <button type="button" onClick={onDiscard}>{discardLabel}</button>}
          </div>
        )}
      </div>
    </div>
  );
}

function ChatMessageQuote({ author, text, gone, label, onClick }: ChatQuote) {
  if (gone || !onClick) {
    return <span className={`sm-msg__quote${gone ? ' sm-msg__quote--gone' : ''}`}><CornerUpLeft size={13} aria-hidden />{author && <b>{author}</b>}<span>{text}</span></span>;
  }
  return (
    <button type="button" className="sm-msg__quote" aria-label={label} onClick={onClick}>
      <CornerUpLeft size={13} aria-hidden />{author && <b>{author}</b>}<span>{text}</span>
    </button>
  );
}

export interface ChatReplyBarProps {
  // "Replying to <b>Ana</b>"
  title: ReactNode;
  // one line of the message being answered
  preview: string;
  cancelLabel: string;
  onCancel: () => void;
}

// above the message box while you answer a message, with an X to stop
export function ChatReplyBar({ title, preview, cancelLabel, onCancel }: ChatReplyBarProps) {
  return (
    <div className="sm-composer__bar">
      <Reply size={15} aria-hidden />
      <span className="sm-composer__bar-text">{title}<span className="sm-composer__bar-preview"> · {preview}</span></span>
      <IconButton icon={X} label={cancelLabel} size="sm" variant="ghost" onClick={onCancel} />
    </div>
  );
}

export interface ChatMessageEditorProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  onSave: () => void;
  onCancel: () => void;
  // "Esc to cancel · Enter to save", with the keys in <kbd>
  hint: ReactNode;
  error?: string;
}

// a message's text being edited in place: Enter saves, Esc cancels, Shift+Enter adds a line
export function ChatMessageEditor({ label, value, onChange, onSave, onCancel, hint, error }: ChatMessageEditorProps) {
  const id = useId();
  const input = useRef<HTMLTextAreaElement>(null);
  // opens with the cursor at the end of the text
  useEffect(() => {
    const box = input.current;
    if (!box) return;
    box.focus();
    box.setSelectionRange(box.value.length, box.value.length);
  }, []);
  useLayoutEffect(() => {
    const box = input.current;
    if (!box) return;
    box.style.height = 'auto';
    box.style.height = `${Math.min(box.scrollHeight, COMPOSER_MAX_HEIGHT)}px`;
  }, [value]);

  return (
    <div className="sm-msg__edit">
      <label className="sm-visually-hidden" htmlFor={`${id}-input`}>{label}</label>
      <textarea ref={input} id={`${id}-input`} rows={1} value={value}
        aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : `${id}-hint`}
        onChange={event => onChange(event.target.value)}
        onKeyDown={event => {
          if (event.key === 'Escape') {
            event.preventDefault();
            onCancel();
          } else if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
            event.preventDefault();
            onSave();
          }
        }} />
      {error && <span className="sm-composer__error" id={`${id}-error`} role="alert">{error}</span>}
      <span className="sm-msg__edit-hint" id={`${id}-hint`}>{hint}</span>
    </div>
  );
}

export interface ChatComposerProps {
  label: string;
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  sendLabel: string;
  // the most characters a message can have; the count shows in the last 200
  max: number;
  error?: string;
  // Arrow up in an empty box, to edit your last message
  onEditLast?: () => boolean;
  // lets the page put the cursor back in the box, after editing a message
  inputRef?: Ref<{ focus: () => void }>;
  // above the box, like the ChatReplyBar
  above?: ReactNode;
  // Esc in the box, like stopping a reply; true when it did something
  onEscape?: () => boolean;
}

const COMPOSER_MAX_HEIGHT = 160;

// the message box: grows with the text, Enter sends and Shift+Enter adds a line
export function ChatComposer({ label, placeholder, value, onChange, onSend, sendLabel, max, error, onEditLast, inputRef, above, onEscape }: ChatComposerProps) {
  const id = useId();
  const input = useRef<HTMLTextAreaElement>(null);
  useImperativeHandle(inputRef, () => ({ focus: () => input.current?.focus() }), []);
  // counted without the spaces around it, like the backend does before its limit
  const length = value.trim().length;
  const over = length > max;
  const canSend = length > 0 && !over;

  useLayoutEffect(() => {
    const box = input.current;
    if (!box) return;
    box.style.height = 'auto';
    box.style.height = `${Math.min(box.scrollHeight, COMPOSER_MAX_HEIGHT)}px`;
  }, [value]);

  const send = () => {
    if (canSend) onSend();
    input.current?.focus();
  };

  return (
    <div className="sm-composer">
      {above}
      <div className="sm-composer__box">
        <label className="sm-visually-hidden" htmlFor={`${id}-input`}>{label}</label>
        <textarea ref={input} id={`${id}-input`} className="sm-composer__input" rows={1} placeholder={placeholder} value={value}
          aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined}
          onChange={event => onChange(event.target.value)}
          onKeyDown={event => {
            if (event.key === 'Escape' && onEscape?.()) {
              event.preventDefault();
              return;
            }
            if (event.key === 'ArrowUp' && !value && onEditLast?.()) {
              event.preventDefault();
              return;
            }
            if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return;
            event.preventDefault();
            send();
          }} />
        <button type="button" className="sm-composer__send" aria-label={sendLabel} disabled={!canSend} onClick={send}>
          <ArrowUp aria-hidden />
        </button>
      </div>
      {(error || length > max - 200) && (
        <div className="sm-composer__foot">
          {error && <span className="sm-composer__error" id={`${id}-error`} role="alert">{error}</span>}
          {length > max - 200 && (
            <span className={`sm-composer__count${over ? ' sm-composer__count--over' : ''}`}>{length} / {max}</span>
          )}
        </div>
      )}
    </div>
  );
}
