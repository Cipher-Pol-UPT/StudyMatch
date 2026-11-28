import { useEffect, useRef, useState, type MouseEvent } from 'react';
import { createPortal } from 'react-dom';
import { Button } from '../../components/button/Button';
import { SearchField } from '../../components/field/SearchField';
import { Skeleton } from '../../components/progress/Progress';
import { useAnchoredPanel } from './useAnchoredPanel';
import './chat.css';

const WIDTH = 392;
const HEIGHT = 460;
// grey boxes while they load
const PLACEHOLDERS = 8;

export interface ChatGifItem {
  id: string;
  title: string;
  url: string;
  width: number;
  height: number;
}

// what the grid shows: grey boxes, the GIFs, or a message, with Try again when a call failed
export type ChatGifResults =
  | { status: 'loading' }
  | { status: 'ready'; gifs: ChatGifItem[] }
  | { status: 'empty'; message: string }
  | { status: 'failed'; message: string; retryLabel: string; onRetry: () => void };

export interface ChatGifPickerProps {
  // the button that opened it: it sits above it (below when there's no room), and gets the focus back on Esc
  anchor: HTMLElement;
  label: string;
  searchLabel: string;
  query: string;
  onQueryChange: (query: string) => void;
  results: ChatGifResults;
  // "Powered by GIPHY", which GIPHY asks for
  poweredByLabel: string;
  onPick: (gif: ChatGifItem) => void;
  onClose: () => void;
}

// picks one GIF, over the conversation: the search on top, the GIFs in two columns that take turns, so they keep
// GIPHY's order left to right without gaps under the shorter ones
export function ChatGifPicker({ anchor, label, searchLabel, query, onQueryChange, results, poweredByLabel, onPick, onClose }: ChatGifPickerProps) {
  const { box, host } = useAnchoredPanel<HTMLDivElement>(anchor, onClose, { width: WIDTH, height: HEIGHT });
  const search = useRef<HTMLInputElement>(null);

  // the search has the focus, so typing searches straight away
  useEffect(() => search.current?.focus(), []);

  return createPortal(
    <div ref={box} className="sm-gifs" role="dialog" aria-label={label}>
      <div className="sm-gifs__head">
        <SearchField ref={search} size="sm" value={query} onValueChange={onQueryChange} placeholder={searchLabel} />
      </div>
      <div className="sm-gifs__list" aria-busy={results.status === 'loading'}>
        {results.status === 'loading' && (
          <div className="sm-gifs__grid" aria-hidden>
            {Array.from({ length: PLACEHOLDERS }, (_, index) => <Skeleton key={index} h={112} r="var(--sm-r-md)" />)}
          </div>
        )}
        {results.status === 'ready' && (
          <div className="sm-gifs__grid">
            {[0, 1].map(column => (
              <div key={column} className="sm-gifs__column">
                {results.gifs.filter((_, index) => index % 2 === column).map(gif => (
                  <button key={gif.id} type="button" className="sm-gifs__item" aria-label={gif.title || label} title={gif.title || undefined}
                    style={{ aspectRatio: `${gif.width} / ${gif.height}` }} onClick={() => onPick(gif)}>
                    <img src={gif.url} alt="" loading="lazy" />
                  </button>
                ))}
              </div>
            ))}
          </div>
        )}
        {(results.status === 'empty' || results.status === 'failed') && (
          <div className="sm-gifs__empty" role="status">
            {results.message}
            {results.status === 'failed' && <Button variant="secondary" size="sm" onClick={results.onRetry}>{results.retryLabel}</Button>}
          </div>
        )}
      </div>
      <div className="sm-gifs__foot">{poweredByLabel}</div>
    </div>,
    host,
  );
}

export interface ChatGifButtonProps {
  label: string;
  open: boolean;
  // gets the button, which the picker sits next to
  onOpen: (anchor: HTMLElement) => void;
}

// the button in the message box that opens the picker
export function ChatGifButton({ label, open, onOpen }: ChatGifButtonProps) {
  return (
    <button type="button" className="sm-composer__tool sm-composer__tool--gif" aria-label={label} title={label} aria-haspopup="dialog" aria-expanded={open}
      onClick={(event: MouseEvent<HTMLButtonElement>) => onOpen(event.currentTarget)}>
      GIF
    </button>
  );
}

export interface ChatGifProps {
  // undefined while it loads, null when it can't be shown
  gif: ChatGifItem | null | undefined;
  unavailableLabel: string;
}

// a GIF in a message. The box has the same size loaded or not, so the messages don't jump when it comes
export function ChatGif({ gif, unavailableLabel }: ChatGifProps) {
  const [broken, setBroken] = useState(false);
  if (gif === null || broken) return <span className="sm-gif sm-gif--unavailable">{unavailableLabel}</span>;
  if (gif === undefined) return <Skeleton className="sm-gif" r="var(--sm-r-lg)" />;
  return <img className="sm-gif" src={gif.url} alt={gif.title} onError={() => setBroken(true)} />;
}
