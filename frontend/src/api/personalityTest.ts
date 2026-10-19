import type { PersonalityProfileData } from '../types/personalityProfile';
import { request } from './client';

export function submitPersonalityTest(answers: (number | null)[]): Promise<PersonalityProfileData> {
  return request<PersonalityProfileData>('POST', '/students/personality-test', { answers });
}
