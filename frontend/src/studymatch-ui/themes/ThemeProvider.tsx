import { useCallback, useEffect, useLayoutEffect, useMemo, useState, type ReactNode } from 'react';
import { ThemeContext } from './ThemeContext';
import { DEFAULT_THEME, isThemeId, THEMES, THEME_STORAGE_KEY, type ThemeId } from './themes';

export interface ThemeProviderProps {
  children: ReactNode;
  defaultTheme?: ThemeId;
  storageKey?: string;
}

function readTheme(storageKey: string, fallback: ThemeId): ThemeId {
  try {
    const saved = window.localStorage.getItem(storageKey);
    return isThemeId(saved) ? saved : fallback;
  } catch {
    return fallback;
  }
}

export function ThemeProvider({ children, defaultTheme = DEFAULT_THEME, storageKey = THEME_STORAGE_KEY }: ThemeProviderProps) {
  const [theme, setThemeState] = useState<ThemeId>(() => readTheme(storageKey, defaultTheme));

  const setTheme = useCallback((next: ThemeId) => {
    if (!isThemeId(next)) return;
    setThemeState(next);
    try {
      window.localStorage.setItem(storageKey, next);
    } catch {
      // Storage can be blocked; the selected theme still applies for this session.
    }
  }, [storageKey]);

  useLayoutEffect(() => {
    const definition = THEMES.find((item) => item.id === theme)!;
    document.documentElement.setAttribute('data-sm-theme', theme);
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', definition.swatches.canvas);
  }, [theme]);

  useEffect(() => {
    const synchronize = (event: StorageEvent) => {
      if (event.key !== storageKey && event.key !== null) return;
      setThemeState(isThemeId(event.newValue) ? event.newValue : defaultTheme);
    };
    window.addEventListener('storage', synchronize);
    return () => window.removeEventListener('storage', synchronize);
  }, [defaultTheme, storageKey]);

  const value = useMemo(() => ({ theme, setTheme }), [theme, setTheme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
