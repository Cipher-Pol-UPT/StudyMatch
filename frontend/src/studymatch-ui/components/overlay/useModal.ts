import { useEffect, useRef, type RefObject } from 'react';

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

let lockCount = 0;
let savedOverflow = '';
let savedPadding = '';

function lockScroll() {
  if (lockCount++ > 0) return;
  const body = document.body;
  const gap = window.innerWidth - document.documentElement.clientWidth;
  savedOverflow = body.style.overflow;
  savedPadding = body.style.paddingRight;
  body.style.overflow = 'hidden';
  if (gap > 0) body.style.paddingRight = `${gap}px`;
}

function unlockScroll() {
  if (--lockCount > 0) return;
  document.body.style.overflow = savedOverflow;
  document.body.style.paddingRight = savedPadding;
}

export function focusableIn(root: HTMLElement) {
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((el) => el.offsetParent !== null || el === document.activeElement);
}

/**
 * Everything a modal surface needs: scroll lock, focus moved inside and trapped,
 * Escape to close, and focus returned to the trigger on close.
 */
export function useModal(open: boolean, onClose: () => void, panel: RefObject<HTMLElement | null>, { dismissible = true, initialFocus }: { dismissible?: boolean; initialFocus?: RefObject<HTMLElement | null> } = {}) {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    lockScroll();
    const raf = requestAnimationFrame(() => {
      const root = panel.current;
      if (!root) return;
      const target = initialFocus?.current ?? root.querySelector<HTMLElement>('[data-autofocus]') ?? focusableIn(root).find((el) => !el.hasAttribute('data-close')) ?? root;
      target.focus({ preventScroll: true });
    });
    const onKey = (e: KeyboardEvent) => {
      const root = panel.current;
      if (!root) return;
      if (e.key === 'Escape' && dismissible) {
        e.stopPropagation();
        onCloseRef.current();
      } else if (e.key === 'Tab') {
        const items = focusableIn(root);
        if (!items.length) {
          e.preventDefault();
          return;
        }
        const first = items[0];
        const last = items[items.length - 1];
        if (e.shiftKey && (document.activeElement === first || !root.contains(document.activeElement))) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener('keydown', onKey);
    return () => {
      cancelAnimationFrame(raf);
      document.removeEventListener('keydown', onKey);
      unlockScroll();
      previous?.focus?.({ preventScroll: true });
    };
  }, [open, dismissible, panel, initialFocus]);
}
