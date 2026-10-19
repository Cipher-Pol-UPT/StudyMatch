import { useEffect,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { ToastProvider } from './components/overlay/Toast';
import { ThemeProvider } from './themes/ThemeProvider';
import type { ThemeId } from './themes/themes';

export interface StudyMatchProviderProps {
  children: ReactNode;
  applyBodyClass?: boolean;
  maxToasts?: number;
  defaultTheme?: ThemeId;
}

export function StudyMatchProvider({ children, applyBodyClass = true, maxToasts = 3, defaultTheme }: StudyMatchProviderProps) {
  const { i18n } = useTranslation();
  useEffect(() => {
    document.documentElement.lang = i18n.language === 'pt' ? 'pt-PT' : 'en-GB';
  }, [i18n.language]);
  useEffect(() => {
    if (!applyBodyClass) return;
    document.body.classList.add('sm-body');
    return () => document.body.classList.remove('sm-body');
  }, [applyBodyClass]);
  return <ThemeProvider defaultTheme={defaultTheme}><ToastProvider max={maxToasts}>{children}</ToastProvider></ThemeProvider>;
}
