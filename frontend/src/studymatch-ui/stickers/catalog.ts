import type { Sticker } from '../../types/enums';

export type StickerId = Sticker;
export type StickerPackId = 'everyday' | 'reactions' | 'exams' | 'christmas' | 'easter' | 'halloween' | 'summer';

// the packs in the picker, each sticker in one. a seasonal pack moves to the top while it's in season
export const STICKER_PACKS: { id: StickerPackId; stickers: StickerId[] }[] = [
  { id: 'everyday', stickers: ['HELLO', 'THANK_YOU', 'ON_IT', 'COFFEE_BREAK'] },
  { id: 'reactions', stickers: ['LAUGHING', 'MIND_BLOWN', 'PARTY', 'LOVE'] },
  { id: 'exams', stickers: ['EXAM_PANIC', 'ALL_NIGHTER', 'PASSED', 'GRADUATED'] },
  { id: 'christmas', stickers: ['SANTA', 'SNOWMAN', 'GIFT', 'REINDEER'] },
  { id: 'easter', stickers: ['BUNNY', 'HATCHING', 'EGG_HUNT', 'CHOCOLATE'] },
  { id: 'halloween', stickers: ['GHOST', 'PUMPKIN', 'WITCH', 'VAMPIRE'] },
  { id: 'summer', stickers: ['SUNGLASSES', 'ICE_CREAM', 'POOL_FLOAT', 'SARDINE'] },
];

// the ones that keep moving in the chat; the rest pop in once
export const ANIMATED_STICKERS: ReadonlySet<StickerId> = new Set<StickerId>([
  'HELLO', 'COFFEE_BREAK', 'LAUGHING', 'MIND_BLOWN', 'PARTY', 'LOVE', 'EXAM_PANIC', 'ALL_NIGHTER', 'PASSED',
  'SNOWMAN', 'HATCHING', 'GHOST', 'PUMPKIN', 'SUNGLASSES', 'POOL_FLOAT',
]);

const DAY = 24 * 60 * 60 * 1000;

// Easter Sunday of that year (the Gregorian computus)
export function easterOf(year: number) {
  const a = year % 19, b = Math.floor(year / 100), c = year % 100;
  const h = (19 * a + b - Math.floor(b / 4) - Math.floor((b - Math.floor((8 * b + 13) / 25)) / 3) + 15) % 30;
  const l = (32 + 2 * (b % 4) + 2 * Math.floor(c / 4) - h - (c % 4)) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31);
  const day = ((h + l - 7 * m + 114) % 31) + 1;
  return new Date(year, month - 1, day);
}

// the seasonal pack for that day, if any: Christmas from mid November to the Epiphany, Easter in the two weeks before
// it and the week after, Halloween in October, summer from June (the Santos Populares) to August
export function packInSeason(date: Date): StickerPackId | undefined {
  const month = date.getMonth();
  const day = date.getDate();
  if ((month === 10 && day >= 15) || month === 11 || (month === 0 && day <= 6)) return 'christmas';
  if (month === 9 || (month === 10 && day <= 2)) return 'halloween';
  if (month >= 5 && month <= 7) return 'summer';
  const today = new Date(date.getFullYear(), month, day).getTime();
  const easter = easterOf(date.getFullYear()).getTime();
  if (today >= easter - 14 * DAY && today <= easter + 7 * DAY) return 'easter';
  return undefined;
}

// the packs in the order the picker shows them
export function orderedPacks(date: Date) {
  const season = packInSeason(date);
  return season ? [...STICKER_PACKS.filter(pack => pack.id === season), ...STICKER_PACKS.filter(pack => pack.id !== season)] : STICKER_PACKS;
}
