import type { Achievement, Language } from './enums';

export interface PersonalityProfileData {
  leadership: number;
  problemSolving: number;
  creativity: number;
  execution: number;
}

export interface PrivateProfileData {
  institutionalEmail: string;
  degree: string;
  curricularYear: number;
  personalityProfile: PersonalityProfileData | null;
  attendance: number | null;
  hasAcademicHistory: boolean;
}

export interface TutorBadgeData {
  subject: string;
  averageRating: number | null;
  ratingCount: number;
}

export interface AchievementBadgeData {
  achievement: Achievement;
  earnedAt: string;
}

export interface StudentProfileData {
  number: number;
  name: string;
  banner: string | null;
  aboutMe: string | null;
  skills: string[];
  interests: string[];
  languages: Language[];
  tutorBadges: TutorBadgeData[];
  achievements: AchievementBadgeData[];
  privateProfile: PrivateProfileData | null;
}

export interface UpdateProfileRequest {
  banner: string | null;
  aboutMe: string;
  skills: string[];
  interests: string[];
  languages: Language[];
}
