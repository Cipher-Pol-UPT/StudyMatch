import { ArrowDown, ArrowUp, ChevronLeft, CircleAlert, Pencil, Trash2 } from 'lucide-react';
import { useEffect, useId, useImperativeHandle, useLayoutEffect, useRef, type MouseEvent, type ReactNode, type Ref, type UIEventHandler } from 'react';
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
  children?: ReactNode;
}

export function ChatThread({ label, head, composer, scrollRef, onScroll, overlay, children }: ChatThreadProps) {
  return (
    <section className="sm-chat__thread" aria-label={label}>
      {head}
      <div className="sm-chat__scroll" ref={scrollRef} onScroll={onScroll}>{children}</div>
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
}

// who the conversation is with; the back button only shows on a phone
export function ChatHead({ name, avatarSrc, subtitle, backLabel, onBack }: ChatHeadProps) {
  return (
    <header className="sm-chat__head">
      <IconButton className="sm-chat__back" icon={ChevronLeft} label={backLabel} variant="ghost" onClick={onBack} />
      <Avatar name={name} src={avatarSrc} size={40} />
      <div className="sm-chat__head-text">
        <h2 className="sm-chat__head-name">{name}</h2>
        {subtitle && <div className="sm-chat__head-sub">{subtitle}</div>}
      </div>
    </header>
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

export interface ChatMessagesProps {
  label: string;
  children: ReactNode;
}

// the messages of a conversation, read out as they arrive
export function ChatMessages({ label, children }: ChatMessagesProps) {
  return <div className="sm-chat__messages" role="log" aria-label={label}>{children}</div>;
}

export interface ChatMessageProps {
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
}

export function ChatMessage({ text, time, dateTime, mine = false, first = true, author, avatarSrc, editedLabel, editedTitle, status, statusLabel, retryLabel, discardLabel, onRetry, onDiscard,
  toolsLabel, editLabel = '', deleteLabel = '', onEdit, onDelete, editor }: ChatMessageProps) {
  const cls = ['sm-msg', mine && 'sm-msg--mine', first && 'sm-msg--first', status === 'sending' && 'sm-msg--sending'].filter(Boolean).join(' ');
  return (
    <div className={cls}>
      {!mine && <div className="sm-msg__avatar">{first && author && <Avatar name={author} src={avatarSrc} size={36} />}</div>}
      <div className="sm-msg__main">
        {first && (
          <div className="sm-msg__meta">
            {!mine && author && <b>{author}</b>}
            <time dateTime={dateTime}>{time}</time>
          </div>
        )}
        {editor ?? (
          <div className="sm-msg__line">
            <div className="sm-msg__bubble">{text}{editedLabel && <span className="sm-msg__edited" title={editedTitle}>{editedLabel}</span>}</div>
            {(onEdit || onDelete) && (
              <div className="sm-msg__tools" role="toolbar" aria-label={toolsLabel}>
                {onEdit && <IconButton icon={Pencil} label={editLabel} size="sm" variant="ghost" onClick={onEdit} />}
                {onDelete && <IconButton icon={Trash2} label={deleteLabel} size="sm" variant="ghost" className="sm-msg__delete"
                  onClick={(event: MouseEvent) => onDelete(event.shiftKey)} />}
              </div>
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
}

const COMPOSER_MAX_HEIGHT = 160;

// the message box: grows with the text, Enter sends and Shift+Enter adds a line
export function ChatComposer({ label, placeholder, value, onChange, onSend, sendLabel, max, error, onEditLast, inputRef }: ChatComposerProps) {
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
      <div className="sm-composer__box">
        <label className="sm-visually-hidden" htmlFor={`${id}-input`}>{label}</label>
        <textarea ref={input} id={`${id}-input`} className="sm-composer__input" rows={1} placeholder={placeholder} value={value}
          aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined}
          onChange={event => onChange(event.target.value)}
          onKeyDown={event => {
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
