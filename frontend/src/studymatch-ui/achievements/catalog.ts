import type { Achievement } from '../../types/enums';
export type AchievementId = Achievement;
export interface AchievementState { id: Achievement; earnedAt?: string; progress?: number }
export const ACHIEVEMENT_ART: Record<Achievement, { tint: string; symbol: string }> = {
  FIRST_STUDYROOM: { tint: 'var(--sm-tint-sky)', symbol: '◈' },
  DEDICATED_TUTOR: { tint: 'var(--sm-tint-mint)', symbol: '✦' },
  TOP_RATED: { tint: 'var(--sm-tint-lemon)', symbol: '★' },
  MENTOR: { tint: 'var(--sm-tint-lavender)', symbol: '✧' },
};
