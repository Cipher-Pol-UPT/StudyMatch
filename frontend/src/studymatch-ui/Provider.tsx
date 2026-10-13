import { useEffect,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
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
  const { i18n } = useTranslation();
  useEffect(() => {
    document.documentElement.lang = i18n.language === 'pt' ? 'pt-PT' : 'en-GB';
  }, [i18n.language]);
  useEffect(() => {
    if (!applyBodyClass) return;
    document.body.classList.add('sm-body');
    return () => document.body.classList.remove('sm-body');
  }, [applyBodyClass]);
  return <ToastProvider max={maxToasts}>{children}</ToastProvider>;
}
