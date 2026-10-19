import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../src/i18n';
import { StudyMatchProvider, ThemeSelector, THEME_STORAGE_KEY, useTheme } from '../src/studymatch-ui';

function CurrentTheme() {
  const { theme } = useTheme();
  return <output aria-label="Current theme">{theme}</output>;
}

let themeColor: HTMLMetaElement;

beforeEach(async () => {
  await i18n.changeLanguage('en');
  localStorage.clear();
  document.documentElement.removeAttribute('data-sm-theme');
  themeColor = document.createElement('meta');
  themeColor.name = 'theme-color';
  themeColor.content = '#ffffff';
  document.head.append(themeColor);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  localStorage.clear();
  document.documentElement.removeAttribute('data-sm-theme');
  themeColor.remove();
});

describe('themes', () => {
  it('applies the default theme through the app provider and updates the browser color', () => {
    render(<StudyMatchProvider defaultTheme="wine-night"><CurrentTheme /></StudyMatchProvider>);

    expect(screen.getByLabelText('Current theme').textContent).toBe('wine-night');
    expect(document.documentElement.getAttribute('data-sm-theme')).toBe('wine-night');
    expect(themeColor.content).toBe('#14060d');
  });

  it('remembers the chosen theme after a fresh app mount', () => {
    const firstMount = render(<StudyMatchProvider><ThemeSelector /></StudyMatchProvider>);
    fireEvent.click(screen.getByRole('radio', { name: 'Spotify, Dark' }));
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('spotify');

    firstMount.unmount();
    // Simulate a reload: retain browser storage, but discard the old React state and DOM theme.
    document.documentElement.removeAttribute('data-sm-theme');
    themeColor.content = '#ffffff';
    render(<StudyMatchProvider><CurrentTheme /></StudyMatchProvider>);

    expect(screen.getByLabelText('Current theme').textContent).toBe('spotify');
    expect(document.documentElement.getAttribute('data-sm-theme')).toBe('spotify');
    expect(themeColor.content).toBe('#121212');
  });

  it('changes the applied theme and radio selection when a theme is clicked', () => {
    render(<StudyMatchProvider><ThemeSelector /><CurrentTheme /></StudyMatchProvider>);
    const original = screen.getByRole('radio', { name: 'StudyMatch, Light' });
    const selected = screen.getByRole('radio', { name: 'Wine Night, Dark' });
    expect(original.getAttribute('aria-checked')).toBe('true');

    fireEvent.click(selected);

    expect(selected.getAttribute('aria-checked')).toBe('true');
    expect(selected.tabIndex).toBe(0);
    expect(original.getAttribute('aria-checked')).toBe('false');
    expect(original.tabIndex).toBe(-1);
    expect(screen.getByLabelText('Current theme').textContent).toBe('wine-night');
    expect(document.documentElement.getAttribute('data-sm-theme')).toBe('wine-night');
    expect(themeColor.content).toBe('#14060d');
  });

  it('still applies a selected theme for the session when storage is blocked', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Storage is blocked', 'SecurityError');
    });
    render(<StudyMatchProvider><ThemeSelector /><CurrentTheme /></StudyMatchProvider>);

    fireEvent.click(screen.getByRole('radio', { name: 'Discord, Dark' }));

    expect(screen.getByLabelText('Current theme').textContent).toBe('discord');
    expect(document.documentElement.getAttribute('data-sm-theme')).toBe('discord');
    expect(themeColor.content).toBe('#313338');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBeNull();
  });
});
