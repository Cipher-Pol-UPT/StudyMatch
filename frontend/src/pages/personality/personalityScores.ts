import { PROFILE_KEYS, type ProfileKey } from '../../studymatch-ui';
import type { PersonalityProfileData } from '../../types/personalityProfile';

export function toPercentages(profile: PersonalityProfileData): Record<ProfileKey, number> {
  return {
    openness: profile.openness * 100,
    conscientiousness: profile.conscientiousness * 100,
    extraversion: profile.extraversion * 100,
    agreeableness: profile.agreeableness * 100,
    neuroticism: profile.neuroticism * 100,
  };
}

// neuroticism is still shown in the bars, but nobody should get "Neurotic" as their strongest trait
const STRENGTHS = PROFILE_KEYS.filter((key) => key !== 'neuroticism');

export function strongestProfile(percentages: Record<ProfileKey, number>): ProfileKey {
  return STRENGTHS.reduce((best, key) => (percentages[key] > percentages[best] ? key : best), STRENGTHS[0]);
}
