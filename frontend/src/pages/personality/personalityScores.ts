import { type ProfileKey } from '../../studymatch-ui';
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
