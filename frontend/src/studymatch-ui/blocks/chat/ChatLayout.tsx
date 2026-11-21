import { ChevronLeft } from 'lucide-react';
import type { ReactNode } from 'react';
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
  children?: ReactNode;
}

export function ChatThread({ label, head, children }: ChatThreadProps) {
  return (
    <section className="sm-chat__thread" aria-label={label}>
      {head}
      <div className="sm-chat__scroll">{children}</div>
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
