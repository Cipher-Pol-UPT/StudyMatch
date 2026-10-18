import { PROFILE_KEYS, type ProfileKey, type StudentProfile } from '../../studymatch-ui';
import type { PersonalityProfileData, PrivateProfileData, StudentProfileData } from '../../types/studentProfile';

export function toProfileView(data: StudentProfileData): StudentProfile {
  const privateProfile = data.privateProfile;
  const personality = privateProfile?.personalityProfile
    ? toPercentages(privateProfile.personalityProfile)
    : undefined;

  return {
    name: data.name,
    studentNumber: String(data.number),
    institutionalEmail: privateProfile?.institutionalEmail ?? '',
    programme: privateProfile?.degree ?? '',
    curricularYear: privateProfile?.curricularYear ?? 0,
    bannerUrl: data.banner,
    aboutMe: data.aboutMe ?? undefined,
    skills: data.skills,
    interests: data.interests,
    languages: data.languages,
    personality,
    dominantProfile: personality ? strongestProfile(personality) : undefined,
    attendance: toAttendance(privateProfile),
    tutorBadges: data.tutorBadges.map(badge => ({
      subject: badge.subject,
      rating: badge.averageRating ?? undefined,
      ratings: badge.ratingCount,
    })),
    achievements: data.achievements.map(earned => ({ id: earned.achievement, earnedAt: earned.earnedAt })),
  };
}

function toPercentages(profile: PersonalityProfileData): Record<ProfileKey, number> {
  return {
    leadership: profile.leadership * 100,
    technical: profile.problemSolving * 100,
    creativity: profile.creativity * 100,
    execution: profile.execution * 100,
  };
}

function strongestProfile(personality: Record<ProfileKey, number>): ProfileKey {
  return PROFILE_KEYS.reduce((strongest, key) => (personality[key] > personality[strongest] ? key : strongest));
}

function toAttendance(privateProfile: PrivateProfileData | null): number | null | undefined {
  if (!privateProfile?.hasAcademicHistory) {
    return undefined;
  }
  if (privateProfile.attendance === null) {
    return null;
  }
  return Math.round(privateProfile.attendance);
}
