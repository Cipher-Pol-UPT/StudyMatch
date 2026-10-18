import { describe, expect, it } from 'vitest';
import { toProfileView } from '../src/pages/profile/toProfileView';
import type { StudentProfileData } from '../src/types/studentProfile';

const profile: StudentProfileData = {
  number: 50001,
  name: 'Test Student',
  banner: 'data:image/png;base64,iVBORw0KGgo=',
  aboutMe: 'Test about me',
  skills: ['Test Skill'],
  interests: ['Test Interest'],
  languages: ['PORTUGUESE'],
  tutorBadges: [{ subject: 'Test Subject', averageRating: 4.5, ratingCount: 2 }],
  achievements: [{ achievement: 'FIRST_STUDYROOM', earnedAt: '2026-10-10T15:00:00' }],
  privateProfile: {
    institutionalEmail: '50001@students.test.com',
    degree: 'Test Degree',
    curricularYear: 2,
    personalityProfile: { leadership: 0.8, problemSolving: 0.6, creativity: 0.9, execution: 0.4 },
    attendance: 87.5,
    hasAcademicHistory: true,
  },
};

describe('profile view mapping', () => {
  it('maps the backend profile to the library profile', () => {
    const view = toProfileView(profile);

    expect(view.studentNumber).toBe('50001');
    expect(view.programme).toBe('Test Degree');
    expect(view.institutionalEmail).toBe('50001@students.test.com');
    expect(view.bannerUrl).toBe('data:image/png;base64,iVBORw0KGgo=');
    expect(view.tutorBadges).toEqual([{ subject: 'Test Subject', rating: 4.5, ratings: 2 }]);
    expect(view.achievements).toEqual([{ id: 'FIRST_STUDYROOM', earnedAt: '2026-10-10T15:00:00' }]);
  });

  it('shows the personality profile as percentages, with problem solving as the technical profile', () => {
    const view = toProfileView(profile);

    expect(view.personality).toEqual({ leadership: 80, technical: 60, creativity: 90, execution: 40 });
    expect(view.dominantProfile).toBe('creativity');
  });

  it('rounds the attendance', () => {
    expect(toProfileView(profile).attendance).toBe(88);
  });

  it('hides the attendance without academic history', () => {
    const view = toProfileView({ ...profile, privateProfile: { ...profile.privateProfile!, hasAcademicHistory: false } });

    expect(view.attendance).toBeUndefined();
  });

  it('keeps a missing attendance as not applicable when there is academic history', () => {
    const view = toProfileView({ ...profile, privateProfile: { ...profile.privateProfile!, attendance: null } });

    expect(view.attendance).toBeNull();
  });

  it('leaves a tutor badge without ratings without a rating', () => {
    const view = toProfileView({ ...profile, tutorBadges: [{ subject: 'Test Subject', averageRating: null, ratingCount: 0 }] });

    expect(view.tutorBadges).toEqual([{ subject: 'Test Subject', rating: undefined, ratings: 0 }]);
  });

  it('shows only the public part when there is no private profile', () => {
    const view = toProfileView({ ...profile, privateProfile: null });

    expect(view.personality).toBeUndefined();
    expect(view.dominantProfile).toBeUndefined();
    expect(view.attendance).toBeUndefined();
    expect(view.institutionalEmail).toBe('');
  });
});
