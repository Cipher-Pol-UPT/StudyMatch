import { ArrowUp, ChevronLeft, CircleAlert } from 'lucide-react';
import { useId, useLayoutEffect, useRef, type ReactNode, type Ref } from 'react';
import { Avatar } from '../../components/avatar/Avatar';
import { IconButton } from '../../components/button/Button';
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
      <div className="sm-chat__list-head">
        <div className="sm-chat__list-title">
          <h1>{title}</h1>
          {actions && <div className="sm-chat__new">{actions}</div>}
        </div>
      </div>
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
  children?: ReactNode;
}

export function ChatThread({ label, head, composer, scrollRef, children }: ChatThreadProps) {
  return (
    <section className="sm-chat__thread" aria-label={label}>
      {head}
      <div className="sm-chat__scroll" ref={scrollRef}>{children}</div>
      {composer}
    </section>
  );
}

export interface ChatHeadProps {
  name: string;
  subtitle?: string;
  backLabel: string;
  onBack: () => void;
}

// who the conversation is with; the back button only shows on a phone
export function ChatHead({ name, subtitle, backLabel, onBack }: ChatHeadProps) {
  return (
    <header className="sm-chat__head">
      <IconButton className="sm-chat__back" icon={ChevronLeft} label={backLabel} variant="ghost" onClick={onBack} />
      <Avatar name={name} size={40} />
      <div className="sm-chat__head-text">
        <h2 className="sm-chat__head-name">{name}</h2>
        {subtitle && <div className="sm-chat__head-sub">{subtitle}</div>}
      </div>
    </header>
  );
}

export interface ChatStartProps {
  name: string;
  children?: ReactNode;
}

// the top of a conversation: who it's with, and a word on how to begin
export function ChatStart({ name, children }: ChatStartProps) {
  return (
    <div className="sm-chat__start">
      <Avatar name={name} size={64} />
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
  // 'sending' until the backend confirms it, 'failed' with Retry and Discard
  status?: 'sending' | 'failed';
  statusLabel?: string;
  retryLabel?: string;
  discardLabel?: string;
  onRetry?: () => void;
  onDiscard?: () => void;
}

export function ChatMessage({ text, time, dateTime, mine = false, first = true, author, status, statusLabel, retryLabel, discardLabel, onRetry, onDiscard }: ChatMessageProps) {
  const cls = ['sm-msg', mine && 'sm-msg--mine', first && 'sm-msg--first', status === 'sending' && 'sm-msg--sending'].filter(Boolean).join(' ');
  return (
    <div className={cls}>
      {!mine && <div className="sm-msg__avatar">{first && author && <Avatar name={author} size={36} />}</div>}
      <div className="sm-msg__main">
        {first && (
          <div className="sm-msg__meta">
            {!mine && author && <b>{author}</b>}
            <time dateTime={dateTime}>{time}</time>
          </div>
        )}
        <div className="sm-msg__bubble">{text}</div>
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
}

const COMPOSER_MAX_HEIGHT = 160;

// the message box: grows with the text, Enter sends and Shift+Enter adds a line
export function ChatComposer({ label, placeholder, value, onChange, onSend, sendLabel, max, error }: ChatComposerProps) {
  const id = useId();
  const input = useRef<HTMLTextAreaElement>(null);
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
