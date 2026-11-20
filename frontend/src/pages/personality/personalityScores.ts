import { PROFILE_KEYS, type ProfileKey } from '../../studymatch-ui';
import type { PersonalityProfileData } from '../../types/personalityProfile';

export function toPercentages(profile: PersonalityProfileData): Record<ProfileKey, number> {
  return {
    leadership: profile.leadership * 100,
    problemSolving: profile.problemSolving * 100,
    creativity: profile.creativity * 100,
    execution: profile.execution * 100,
  };
}

export function strongestProfile(percentages: Record<ProfileKey, number>): ProfileKey {
  return PROFILE_KEYS.reduce((best, key) => (percentages[key] > percentages[best] ? key : best));
}
