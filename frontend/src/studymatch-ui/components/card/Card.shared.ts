import type { CardTone } from './Card';
import './card.css';

export const isDarkTone = (tone: CardTone) => DARK.includes(tone);

export const DARK: CardTone[] = ['violet', 'black', 'ink', 'gradient'];
