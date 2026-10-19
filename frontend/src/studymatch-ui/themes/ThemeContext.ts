import { createContext, useContext } from 'react';
import type { ThemeId } from './themes';

interface ThemeContextValue {
  theme: ThemeId;
  setTheme: (theme: ThemeId) => void;
}

export const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used within StudyMatchProvider or ThemeProvider');
  return context;
}
