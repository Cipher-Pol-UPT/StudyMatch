import { hashString } from '../../utils/shared';
import '../button/button.css';
import './sticky-note.css';
import type { NoteColor } from './StickyNote';

export const NOTE_COLORS: NoteColor[] = ['yellow', 'pink', 'blue', 'green', 'orange', 'violet'];

export const NOTE_HEX: Record<NoteColor, string> = {
  yellow: '#ffe66d',
  pink: '#ffb8d2',
  blue: '#ade0ff',
  green: '#bff2c6',
  orange: '#ffca94',
  violet: '#d9d0ff',
};

export function tiltFor(id: string) {
  return ((hashString(id) % 70) - 35) / 10; 
}
