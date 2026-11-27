import { SearchX } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { searchChat } from '../../api/chats';
import { isBackendError } from '../../api/errors';
import { DataLoadState } from '../../components/feedback/DataLoadState';
import { Banner, Button, ChatResult, ChatSheet, EmptyState } from '../../studymatch-ui';
import type { ChatContactData, MessageData } from '../../types/chat';

// the backend refuses anything shorter, without the spaces around it
export const SEARCH_MIN_LENGTH = 2;
// how long typing has to stop before it searches
export const SEARCH_WAIT_MS = 300;
// this many characters before the first match stay in a result, so the match shows in its two lines
const BEFORE_MATCH = 30;

interface Results {
  // what these are the results for
  text: string;
  messages: MessageData[];
  hasMore: boolean;
  loadingMore?: boolean;
  error?: unknown;
}

const escape = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

// the text with every match in <mark>, starting a little before the first one
function highlight(text: string, searched: string): ReactNode[] {
  const first = text.toLowerCase().indexOf(searched.toLowerCase());
  const from = first > BEFORE_MATCH ? first - BEFORE_MATCH : 0;
  const shown = (from ? '…' : '') + text.slice(from);
  return shown.split(new RegExp(`(${escape(searched)})`, 'gi')).map((part, index) => index % 2 ? <mark key={index}>{part}</mark> : part);
}

export interface ChatSearchProps {
  chatId: number;
  // what's typed in the head's search field
  query: string;
  onClose: () => void;
  // goes to the message in the chat; fails if it's gone
  onJump: (id: number) => Promise<void>;
  nameOf: (author: ChatContactData) => string;
}

// the chat's search results, on the right (#221): it waits until you stop typing, newest first, 25 at a time
export function ChatSearch({ chatId, query, onClose, onJump, nameOf }: ChatSearchProps) {
  const { t, i18n } = useTranslation();
  const searched = query.trim();
  const [results, setResults] = useState<Results | null>(null);
  const [jumpError, setJumpError] = useState<unknown>();
  // the latest search asked for, so an older answer that comes late is dropped
  const asked = useRef('');

  useEffect(() => {
    if (searched.length < SEARCH_MIN_LENGTH) return undefined;
    const wait = setTimeout(() => {
      asked.current = searched;
      searchChat(chatId, searched).then(
        page => { if (asked.current === searched) setResults({ text: searched, ...page }); },
        error => { if (asked.current === searched) setResults({ text: searched, messages: [], hasMore: false, error }); },
      );
    }, SEARCH_WAIT_MS);
    return () => clearTimeout(wait);
  }, [chatId, searched]);

  const more = () => {
    if (!results || results.loadingMore) return;
    const { text } = results;
    setResults({ ...results, loadingMore: true });
    searchChat(chatId, text, results.messages[results.messages.length - 1].id).then(
      page => { if (asked.current === text) setResults(current => current && { ...current, messages: [...current.messages, ...page.messages], hasMore: page.hasMore, loadingMore: false }); },
      () => { if (asked.current === text) setResults(current => current && { ...current, loadingMore: false }); },
    );
  };

  const jump = (id: number) => {
    setJumpError(undefined);
    onJump(id).then(
      // on a phone the panel covers the chat, so it closes to show the message
      () => { if (window.matchMedia?.('(max-width: 720px)').matches) onClose(); },
      setJumpError,
    );
  };

  const today = new Date().toDateString();
  const timeFormat = new Intl.DateTimeFormat(i18n.language, { hour: '2-digit', minute: '2-digit' });
  const dayFormat = new Intl.DateTimeFormat(i18n.language, { day: 'numeric', month: 'short', year: 'numeric' });
  const when = (sentAt: string) => {
    const date = new Date(sentAt);
    return date.toDateString() === today ? timeFormat.format(date) : dayFormat.format(date);
  };
  const errorText = (error: unknown) => isBackendError(error) ? t(`errors.${error.code}`, error.params ?? {}) : t('common.unexpectedError');

  // results for what's typed now; anything else is still on its way
  const current = searched.length >= SEARCH_MIN_LENGTH && results?.text === searched ? results : null;
  let body: ReactNode;
  if (searched.length < SEARCH_MIN_LENGTH) body = <p className="sm-chat__sheet-hint">{t('errors.SEARCH_TOO_SHORT', { min: SEARCH_MIN_LENGTH })}</p>;
  else if (!current) body = <DataLoadState />;
  else if (current.error !== undefined) body = <Banner tone="danger">{errorText(current.error)}</Banner>;
  else if (!current.messages.length) body = <EmptyState compact icon={SearchX} title={t('chats.noResults')} body={t('chats.noResultsBody', { text: searched })} />;
  else {
    body = (
      <>
        <ul className="sm-chat__results" aria-label={t('chats.searchResults')}>
          {current.messages.map(message => (
            <ChatResult key={message.id} author={nameOf(message.author)} time={when(message.sentAt)} dateTime={message.sentAt} onClick={() => jump(message.id)}>
              {highlight(message.text, searched)}
            </ChatResult>
          ))}
        </ul>
        {current.hasMore && <Button size="sm" variant="secondary" loading={current.loadingMore} onClick={more}>{t('chats.moreResults')}</Button>}
      </>
    );
  }

  const title = current && !current.error && current.messages.length
    ? t(current.hasMore ? 'chats.resultsMore' : 'chats.results', { count: current.messages.length })
    : t('chats.searchTitle');
  return (
    <ChatSheet label={t('chats.searchResults')} title={title} closeLabel={t('chats.closeSearch')} onClose={onClose}>
      {jumpError !== undefined && (
        <Banner tone="danger">{isBackendError(jumpError) && jumpError.code === 'NOT_FOUND' ? t('chats.resultGone') : errorText(jumpError)}</Banner>
      )}
      {body}
    </ChatSheet>
  );
}
