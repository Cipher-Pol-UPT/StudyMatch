import { Sticker } from '../../types/enums';

// the stickers you sent last, newest first, kept in this browser for the picker's Recently used
const KEY = 'sm-recent-stickers';
const MAX = 8;

export function recentStickers(): Sticker[] {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    // only ones that still exist, in case a sticker is ever taken out
    return Array.isArray(saved) ? saved.filter((id): id is Sticker => (Sticker as readonly unknown[]).includes(id)).slice(0, MAX) : [];
  } catch {
    return [];
  }
}

export function rememberSticker(id: Sticker) {
  try {
    localStorage.setItem(KEY, JSON.stringify([id, ...recentStickers().filter(other => other !== id)].slice(0, MAX)));
  } catch {
    // without storage the picker just has no Recently used
  }
}
