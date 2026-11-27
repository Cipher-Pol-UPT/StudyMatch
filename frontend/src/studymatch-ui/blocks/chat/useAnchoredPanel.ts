import { useEffect, useLayoutEffect, useRef } from 'react';

// space kept between a panel and its button, and the edges of the conversation
const GAP = 6;
const EDGE = 8;

// a panel over the conversation, like the emoji and sticker pickers: it sits above the button that opened it (below
// when there's no room). A click outside, Esc or scrolling the messages closes it, and Esc gives the button the focus
export function useAnchoredPanel<T extends HTMLElement>(anchor: HTMLElement, onClose: () => void, size: { width: number; height: number }) {
  const box = useRef<T>(null);
  const host = (anchor.closest('.sm-chat__thread') as HTMLElement | null) ?? document.body;
  const { width: fallbackWidth, height: fallbackHeight } = size;

  useLayoutEffect(() => {
    const element = box.current;
    if (!element) return;
    const area = host.getBoundingClientRect();
    const button = anchor.getBoundingClientRect();
    const width = element.offsetWidth || fallbackWidth;
    const height = element.offsetHeight || fallbackHeight;
    let top = button.top - area.top - height - GAP;
    if (top < EDGE) top = Math.min(button.bottom - area.top + GAP, area.height - height - EDGE);
    const left = button.left - area.left + button.width / 2 - width / 2;
    element.style.top = `${Math.max(EDGE, top)}px`;
    element.style.left = `${Math.max(EDGE, Math.min(left, area.width - width - EDGE))}px`;
  }, [anchor, host, fallbackWidth, fallbackHeight]);

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

  return { box, host };
}
