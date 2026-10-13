import { useCallback, useId, useRef, useState, type ElementType } from 'react';

/**
 * Any icon component that accepts `size` and `strokeWidth`
 * (lucide-react, @tabler/icons-react, react-feather…).
 */
export type IconComponent = ElementType;

/** Signature ease of the system: strong and decisive, never floaty. */
export const EASE_OUT = [0.16, 1, 0.3, 1] as const;
export const SPRING = { type: 'spring', stiffness: 520, damping: 36 } as const;

/** Stable 32-bit hash for deterministic colours, tilts and patterns. */
export function hashString(value: string) {
  let h = 0;
  for (let i = 0; i < value.length; i++) h = (h * 31 + value.charCodeAt(i)) >>> 0;
  return h;
}

/** `useId` without colons, safe inside SVG `url(#…)` references and CSS selectors. */
export function useSafeId(prefix = 'sm') {
  return `${prefix}-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
}

/**
 * Controlled / uncontrolled state in one hook.
 * Pass `value` to control the component; otherwise it manages `defaultValue` itself.
 */
export function useControllableState<T>(value: T | undefined, defaultValue: T, onChange?: (next: T) => void) {
  const [inner, setInner] = useState(defaultValue);
  const controlled = value !== undefined;
  const current = controlled ? (value as T) : inner;
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const set = useCallback(
    (next: T) => {
      if (!controlled) setInner(next);
      onChangeRef.current?.(next);
    },
    [controlled],
  );
  return [current, set] as const;
}

/** True when the user asked the OS for less motion. */
export function prefersReducedMotion() {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
}

/** Arrow / Home / End handling for horizontal option lists (tabs, segmented, radio rows). */
export function nextIndex(key: string, index: number, count: number, vertical = false) {
  const prev = vertical ? 'ArrowUp' : 'ArrowLeft';
  const next = vertical ? 'ArrowDown' : 'ArrowRight';
  if (key === next) return (index + 1) % count;
  if (key === prev) return (index - 1 + count) % count;
  if (key === 'Home') return 0;
  if (key === 'End') return count - 1;
  return -1;
}

export function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
