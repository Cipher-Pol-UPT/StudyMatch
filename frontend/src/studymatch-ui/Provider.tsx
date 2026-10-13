import { useEffect, type ReactNode } from 'react';
import { ToastProvider } from './components/overlay/Toast';

export interface StudyMatchProviderProps {
  children: ReactNode;
  /** Adds `sm-body` to <body> (font, canvas colour, selection colour). */
  applyBodyClass?: boolean;
  /** Max toasts on screen at once. */
  maxToasts?: number;
}

/** Wrap your app once. Provides toasts and the base page styles. */
export function StudyMatchProvider({ children, applyBodyClass = true, maxToasts = 3 }: StudyMatchProviderProps) {
  useEffect(() => {
    if (!applyBodyClass) return;
    document.body.classList.add('sm-body');
    return () => document.body.classList.remove('sm-body');
  }, [applyBodyClass]);
  return <ToastProvider max={maxToasts}>{children}</ToastProvider>;
}
