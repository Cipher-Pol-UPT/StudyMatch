import type { StudentProfile } from '../../studymatch-ui';
import type { PrivateProfileData, StudentProfileData } from '../../types/studentProfile';
import { strongestProfile, toPercentages } from '../personality/personalityScores';

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
    avatarUrl: data.pfp,
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

function toAttendance(privateProfile: PrivateProfileData | null): number | null | undefined {
  if (!privateProfile?.hasAcademicHistory) {
    return undefined;
  }
  if (privateProfile.attendance === null) {
    return null;
  }
  return Math.round(privateProfile.attendance);
}
