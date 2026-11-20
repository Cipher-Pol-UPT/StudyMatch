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

// the highest of the five traits as the backend measured them, neuroticism included
export function strongestProfile(percentages: Record<ProfileKey, number>): ProfileKey {
  return PROFILE_KEYS.reduce((best, key) => (percentages[key] > percentages[best] ? key : best));
}
