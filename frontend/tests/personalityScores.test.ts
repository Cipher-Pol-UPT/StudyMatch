import { describe, expect, it } from 'vitest';
import { strongestProfile } from '../src/pages/personality/personalityScores';
import { PROFILE_KEYS, type ProfileKey } from '../src/studymatch-ui';

// written with PROFILE_KEYS instead of the trait names, so it doesn't depend on which traits there are
const scores = (score: (key: ProfileKey, index: number) => number) =>
  Object.fromEntries(PROFILE_KEYS.map((key, index) => [key, score(key, index)])) as Record<ProfileKey, number>;

describe('strongestProfile', () => {
  it('picks the highest score', () => {
    const second = PROFILE_KEYS[1];
    expect(strongestProfile(scores(key => (key === second ? 90 : 40)))).toBe(second);
  });

  it('picks the first trait when every score is the same, starting from it instead of failing', () => {
    expect(strongestProfile(scores(() => 0))).toBe(PROFILE_KEYS[0]);
  });

  it('keeps the earlier trait on a tie for the highest score', () => {
    const [, second, third] = PROFILE_KEYS;
    expect(strongestProfile(scores(key => (key === second || key === third ? 70 : 10)))).toBe(second);
  });
});
