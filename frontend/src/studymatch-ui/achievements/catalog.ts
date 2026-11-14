import type { Achievement } from '../../types/enums';
export type AchievementId = Achievement;
export interface AchievementState { id: Achievement; earnedAt?: string; progress?: number }
export type AchievementRarity = 'common' | 'rare' | 'epic' | 'legendary';
export const RARITY: Record<AchievementRarity, { color: string }> = {
  common: { color: 'var(--sm-rarity-common)' },
  rare: { color: 'var(--sm-rarity-rare)' },
  epic: { color: 'var(--sm-rarity-epic)' },
  legendary: { color: 'var(--sm-rarity-legendary)' },
};
// rarity and tint taken from the library's achievement with the same meaning (in brackets)
export const ACHIEVEMENT_ART: Record<Achievement, { rarity: AchievementRarity; tint: [string, string] }> = {
  FIRST_STUDYROOM: { rarity: 'common', tint: ['#ffeee4', '#ffe0a8'] }, // (door-opener)
  DEDICATED_TUTOR: { rarity: 'epic', tint: ['#ffe9f2', '#ffc9df'] }, // (full-house)
  TOP_RATED: { rarity: 'epic', tint: ['#fff6d6', '#ffe08a'] }, // (five-stars)
  MENTOR: { rarity: 'rare', tint: ['#f7fbd8', '#e8f59a'] }, // (mentor)
};
