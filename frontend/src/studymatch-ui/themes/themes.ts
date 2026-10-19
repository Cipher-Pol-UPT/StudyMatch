export const THEMES = [
  { id: 'studymatch', name: 'StudyMatch', mode: 'light', swatches: { canvas: '#f6f6fb', surface: '#ffffff', accent: '#4a3df5', spark: '#d9ff3f', text: '#000000' } },
  { id: 'wine', name: 'Wine', mode: 'light', swatches: { canvas: '#f8f4f5', surface: '#fffcfd', accent: '#7d1453', spark: '#f3d27a', text: '#2a0b1c' } },
  { id: 'wine-night', name: 'Wine Night', mode: 'dark', swatches: { canvas: '#14060d', surface: '#1e0d16', accent: '#d13d91', spark: '#f3d27a', text: '#f3e6ec' } },
  { id: 'cyan-night', name: 'Cyan Night', mode: 'dark', swatches: { canvas: '#041016', surface: '#08171e', accent: '#22cde5', spark: '#ffc857', text: '#ddeff3' } },
  { id: 'discord', name: 'Discord', mode: 'dark', swatches: { canvas: '#313338', surface: '#2b2d31', accent: '#5865f2', spark: '#fee75c', text: '#f2f3f5' } },
  { id: 'spotify', name: 'Spotify', mode: 'dark', swatches: { canvas: '#121212', surface: '#181818', accent: '#1ed760', spark: '#f573a0', text: '#ffffff' } },
] as const;

export type ThemeId = typeof THEMES[number]['id'];
export const DEFAULT_THEME: ThemeId = 'studymatch';
export const THEME_STORAGE_KEY = 'sm-theme';

export function isThemeId(value: unknown): value is ThemeId {
  return THEMES.some((theme) => theme.id === value);
}
