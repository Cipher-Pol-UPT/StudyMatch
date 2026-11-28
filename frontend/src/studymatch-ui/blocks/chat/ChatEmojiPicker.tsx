import type { Categories, Theme } from 'emoji-picker-react';
import { lazy, Suspense } from 'react';
import { createPortal } from 'react-dom';
import { Spinner } from '../../components/progress/Progress';
import { useTheme } from '../../themes/ThemeContext';
import { THEMES } from '../../themes/themes';
import { EMOJI_CATEGORIES } from './emojiCategories';
import { useAnchoredPanel } from './useAnchoredPanel';
import './chat.css';

// the picker is a library with every emoji (#217); it only loads the first time someone opens it
const Picker = lazy(() => import('emoji-picker-react'));

// wide enough for its 8 columns of emoji
const WIDTH = 344;
const HEIGHT = 380;

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
  const { box, host } = useAnchoredPanel<HTMLDivElement>(anchor, onClose, { width: WIDTH, height: HEIGHT });
  const { theme } = useTheme();
  const dark = THEMES.find(option => option.id === theme)?.mode === 'dark';

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
