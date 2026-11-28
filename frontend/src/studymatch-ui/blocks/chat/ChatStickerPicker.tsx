import { Sparkles } from 'lucide-react';
import { useEffect, useRef, useState, type KeyboardEvent, type MouseEvent, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { SearchField } from '../../components/field/SearchField';
import { StickerArt } from '../../stickers/StickerArt';
import type { StickerId } from '../../stickers/catalog';
import { useAnchoredPanel } from './useAnchoredPanel';
import './chat.css';

const WIDTH = 392;
const HEIGHT = 460;
// stickers on a row, for the arrow keys
const COLUMNS = 4;

export interface ChatStickerItem {
  id: StickerId;
  name: string;
  // keeps moving in the chat
  animated?: boolean;
}

export interface ChatStickerPack {
  id: string;
  name: string;
  // a seasonal pack while it's in season, with a chip next to its name
  inSeason?: boolean;
  // on the rail instead of its first sticker, like a clock for the recent ones
  icon?: ReactNode;
  stickers: ChatStickerItem[];
}

export interface ChatStickerPickerProps {
  // the button that opened it: it sits above it (below when there's no room), and gets the focus back on Esc
  anchor: HTMLElement;
  label: string;
  searchLabel: string;
  // at the bottom until a sticker is pointed at, like how to send one with the keyboard
  hint: string;
  inSeasonLabel: string;
  animatedLabel: string;
  // when the search finds nothing
  emptyLabel: (query: string) => string;
  packs: ChatStickerPack[];
  onPick: (id: StickerId) => void;
  onClose: () => void;
}

interface Pointed extends ChatStickerItem { pack: string }

// picks one sticker, over the conversation. The search finds them by name, the rail jumps to a pack, and the one
// pointed at comes alive and is shown bigger at the bottom. Arrows move between them, Enter sends
export function ChatStickerPicker({ anchor, label, searchLabel, hint, inSeasonLabel, animatedLabel, emptyLabel, packs, onPick, onClose }: ChatStickerPickerProps) {
  const { box, host } = useAnchoredPanel<HTMLDivElement>(anchor, onClose, { width: WIDTH, height: HEIGHT });
  const list = useRef<HTMLDivElement>(null);
  const search = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState('');
  const [pointed, setPointed] = useState<Pointed>();
  const [current, setCurrent] = useState(packs[0]?.id);

  // the search has the focus, so typing finds one straight away
  useEffect(() => search.current?.focus(), []);

  const wanted = query.trim().toLocaleLowerCase();
  // each sticker once, in the order of the packs
  const found = wanted ? [...new Map(packs.flatMap(pack => pack.stickers
    .filter(sticker => sticker.name.toLocaleLowerCase().includes(wanted))
    .map(sticker => [sticker.id, { ...sticker, pack: pack.name }] as const))).values()] : [];

  const items = () => [...(list.current?.querySelectorAll<HTMLButtonElement>('.sm-stickers__item') ?? [])];
  const sections = () => [...(list.current?.querySelectorAll<HTMLElement>('.sm-stickers__pack') ?? [])];
  const jump = (id: string) => {
    setQuery('');
    setCurrent(id);
    // after the search is cleared and every pack is back
    requestAnimationFrame(() => {
      const section = sections().find(element => element.dataset.pack === id);
      if (!section || !list.current) return;
      list.current.scrollTo({ top: section.offsetTop - list.current.offsetTop, behavior: 'smooth' });
    });
  };
  // the pack being looked at is the last one whose name is above the top third of the list
  const scrolled = () => {
    const area = list.current;
    if (!area || wanted) return;
    const line = area.scrollTop + area.clientHeight / 3;
    const seen = sections().filter(section => section.offsetTop - area.offsetTop <= line).pop();
    if (seen?.dataset.pack) setCurrent(seen.dataset.pack);
  };

  // on each sticker, so the keys work from the one that has focus
  const arrows = (event: KeyboardEvent<HTMLButtonElement>) => {
    const steps: Record<string, number> = { ArrowRight: 1, ArrowLeft: -1, ArrowDown: COLUMNS, ArrowUp: -COLUMNS };
    const step = steps[event.key];
    if (!step) return;
    const all = items();
    const at = all.indexOf(event.currentTarget);
    if (at < 0) return;
    event.preventDefault();
    // up from the first row goes back to the search
    if (at + step < 0 && event.key === 'ArrowUp') search.current?.focus();
    else all[Math.max(0, Math.min(all.length - 1, at + step))].focus();
  };
  const fromSearch = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      items()[0]?.focus();
    } else if (event.key === 'Enter' && found.length > 0) {
      event.preventDefault();
      onPick(found[0].id);
    }
  };

  const item = (sticker: Pointed) => (
    <button key={sticker.id} type="button" className="sm-stickers__item" aria-label={sticker.animated ? `${sticker.name}, ${animatedLabel}` : sticker.name}
      onPointerEnter={() => setPointed(sticker)} onFocus={() => setPointed(sticker)} onKeyDown={arrows} onClick={() => onPick(sticker.id)}>
      <StickerArt id={sticker.id} size={68} title="" className={pointed?.id === sticker.id ? 'sm-sticker--alive' : undefined} />
      {sticker.animated && <span className="sm-stickers__badge" aria-hidden><Sparkles size={10} strokeWidth={2.5} /></span>}
    </button>
  );

  return createPortal(
    <div ref={box} className="sm-stickers" role="dialog" aria-label={label}>
      <nav className="sm-stickers__rail" aria-label={label}>
        {packs.map(pack => (
          <button key={pack.id} type="button" className="sm-stickers__tab" aria-label={pack.name} title={pack.name}
            aria-current={!wanted && current === pack.id ? 'true' : undefined} onClick={() => jump(pack.id)}>
            {pack.icon ?? <StickerArt id={pack.stickers[0].id} size={32} title="" />}
          </button>
        ))}
      </nav>
      <div className="sm-stickers__head">
        <SearchField ref={search} size="sm" value={query} onValueChange={setQuery} placeholder={searchLabel} onKeyDown={fromSearch} />
      </div>
      <div ref={list} className="sm-stickers__list" onScroll={scrolled} onPointerLeave={() => setPointed(undefined)}>
        {wanted ? (
          found.length > 0
            ? <div className="sm-stickers__grid sm-stickers__grid--found">{found.map(item)}</div>
            : (
              <div className="sm-stickers__empty" role="status">
                <StickerArt id="EXAM_PANIC" size={84} title="" />
                {emptyLabel(query.trim())}
              </div>
            )
        ) : packs.map(pack => (
          <section key={pack.id} className="sm-stickers__pack" data-pack={pack.id} aria-label={pack.name}>
            <h3 className="sm-stickers__name">
              {pack.name}
              {pack.inSeason && <span className="sm-stickers__season">{inSeasonLabel}</span>}
            </h3>
            <div className="sm-stickers__grid">{pack.stickers.map(sticker => item({ ...sticker, pack: pack.name }))}</div>
          </section>
        ))}
      </div>
      <div className="sm-stickers__foot" aria-hidden>
        {pointed ? (
          <>
            <StickerArt key={pointed.id} id={pointed.id} size={52} title="" animated className="sm-sticker--pop" />
            <span className="sm-stickers__named">
              <b>{pointed.name}</b>
              <span>{pointed.pack}{pointed.animated && <span className="sm-stickers__animated"><Sparkles size={11} strokeWidth={2.5} />{animatedLabel}</span>}</span>
            </span>
          </>
        ) : <span className="sm-stickers__hint">{hint}</span>}
      </div>
    </div>,
    host,
  );
}

export interface ChatStickerButtonProps {
  label: string;
  open: boolean;
  // gets the button, which the picker sits next to
  onOpen: (anchor: HTMLElement) => void;
}

// the button in the message box that opens the picker: a tiny Matchy waving, that hops when pointed at
export function ChatStickerButton({ label, open, onOpen }: ChatStickerButtonProps) {
  return (
    <button type="button" className="sm-composer__tool" aria-label={label} title={label} aria-haspopup="dialog" aria-expanded={open}
      onClick={(event: MouseEvent<HTMLButtonElement>) => onOpen(event.currentTarget)}>
      <StickerArt id="HELLO" size={30} title="" />
    </button>
  );
}
