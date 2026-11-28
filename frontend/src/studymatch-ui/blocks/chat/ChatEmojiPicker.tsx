import type { Categories, Theme } from 'emoji-picker-react';
import { lazy, Suspense, useEffect, useLayoutEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { Spinner } from '../../components/progress/Progress';
import { useTheme } from '../../themes/ThemeContext';
import { THEMES } from '../../themes/themes';
import { EMOJI_CATEGORIES } from './emojiCategories';
import './chat.css';

// the picker is a library with every emoji (#217); it only loads the first time someone opens it
const Picker = lazy(() => import('emoji-picker-react'));

// wide enough for its 8 columns of emoji
const WIDTH = 344;
const HEIGHT = 380;
// space kept between the picker and the button, and the edges of the conversation
const GAP = 6;
const EDGE = 8;


export interface ChatEmojiPickerProps {
  // the button that opened it: it sits above it (below when there's no room), and gets the focus back on Esc
  anchor: HTMLElement;
  label: string;
  searchLabel: string;
  loadingLabel: string;
  // each group's name, by its key in EMOJI_CATEGORIES
  categoryNames: Record<(typeof EMOJI_CATEGORIES)[number], string>;
  onPick: (emoji: string) => void;
  onClose: () => void;
}

// picks one emoji, over the conversation. A click outside, Esc or scrolling the messages closes it
export function ChatEmojiPicker({ anchor, label, searchLabel, loadingLabel, categoryNames, onPick, onClose }: ChatEmojiPickerProps) {
  const box = useRef<HTMLDivElement>(null);
  const { theme } = useTheme();
  const dark = THEMES.find(option => option.id === theme)?.mode === 'dark';
  const host = (anchor.closest('.sm-chat__thread') as HTMLElement | null) ?? document.body;

  useLayoutEffect(() => {
    const element = box.current;
    if (!element) return;
    const area = host.getBoundingClientRect();
    const button = anchor.getBoundingClientRect();
    const width = element.offsetWidth || WIDTH;
    const height = element.offsetHeight || HEIGHT;
    let top = button.top - area.top - height - GAP;
    if (top < EDGE) top = Math.min(button.bottom - area.top + GAP, area.height - height - EDGE);
    const left = button.left - area.left + button.width / 2 - width / 2;
    element.style.top = `${Math.max(EDGE, top)}px`;
    element.style.left = `${Math.max(EDGE, Math.min(left, area.width - width - EDGE))}px`;
  }, [anchor, host]);

  useEffect(() => {
    const outside = (event: PointerEvent) => {
      const target = event.target as Node;
      // the button that opened it closes it itself
      if (!box.current?.contains(target) && !anchor.contains(target)) onClose();
    };
    const key = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      onClose();
      anchor.focus();
    };
    const messages = host.querySelector('.sm-chat__scroll');
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', key);
    messages?.addEventListener('scroll', onClose);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', key);
      messages?.removeEventListener('scroll', onClose);
    };
  }, [anchor, host, onClose]);

  return createPortal(
    <div ref={box} className="sm-emoji" role="dialog" aria-label={label}>
      <Suspense fallback={<div className="sm-emoji__loading"><Spinner size={20} label={loadingLabel} /></div>}>
        <Picker width={WIDTH} height={HEIGHT} theme={(dark ? 'dark' : 'light') as Theme}
          // the system's own emoji, so nothing is downloaded from a CDN
          emojiStyle={'native' as never} lazyLoadEmojis previewConfig={{ showPreview: false }}
          searchPlaceholder={searchLabel} searchPlaceHolder={searchLabel}
          categories={EMOJI_CATEGORIES.map(category => ({ category: category as Categories, name: categoryNames[category] }))}
          onEmojiClick={emoji => onPick(emoji.emoji)} />
      </Suspense>
    </div>,
    host,
  );
}
