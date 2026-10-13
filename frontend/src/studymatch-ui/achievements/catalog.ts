/**
 * StudyMatch achievements.
 *
 * Every achievement is awarded automatically by the System (Award Achievement use case)
 * from data the platform already stores: personality tests, profiles, published groups,
 * StudyRoom attendance, tutoring, ratings, attendance and final grades.
 * `trigger` names the domain event that should re-evaluate it, `rule` is the exact check.
 */

export type AchievementRarity = 'common' | 'rare' | 'epic' | 'legendary';
export type AchievementCategory = 'getting-started' | 'teamwork' | 'studyrooms' | 'tutoring' | 'academic';

export type AchievementId =
  | 'know-yourself'
  | 'all-set'
  | 'polyglot'
  | 'team-player'
  | 'squad-veteran'
  | 'new-faces'
  | 'door-opener'
  | 'early-bird'
  | 'night-owl'
  | 'bookworm'
  | 'mentor'
  | 'full-house'
  | 'five-stars'
  | 'kind-words'
  | 'never-missed'
  | 'top-ten'
  | 'clean-sweep'
  | 'category-master'
  | 'perfect-score';

export interface AchievementDef {
  id: AchievementId;
  name: string;
  /** One line: what the achievement means. */
  description: string;
  /** How to earn it. Concrete and measurable. */
  criteria: string;
  category: AchievementCategory;
  rarity: AchievementRarity;
  /** Target for progress bars (e.g. 10 StudyRooms). 1 for one-off achievements. */
  goal: number;
  /** Unit for progress text: "3 / 10 StudyRooms". */
  unit?: string;
  /** Domain event that re-checks this achievement. */
  trigger: string;
  /** Precise rule for the backend. */
  rule: string;
  /** Soft background of the illustration tile [from, to]. */
  tint: [string, string];
  /** Dark tile (light objects on ink). */
  dark?: boolean;
}

export const ACHIEVEMENT_CATEGORIES: Record<AchievementCategory, string> = {
  'getting-started': 'Getting started',
  teamwork: 'Teamwork',
  studyrooms: 'StudyRooms',
  tutoring: 'Tutoring',
  academic: 'Academic',
};

export const RARITY: Record<AchievementRarity, { label: string; color: string; order: number }> = {
  common: { label: 'Common', color: 'var(--sm-rarity-common)', order: 0 },
  rare: { label: 'Rare', color: 'var(--sm-rarity-rare)', order: 1 },
  epic: { label: 'Epic', color: 'var(--sm-rarity-epic)', order: 2 },
  legendary: { label: 'Legendary', color: 'var(--sm-rarity-legendary)', order: 3 },
};

export const ACHIEVEMENTS: AchievementDef[] = [
  // ── Getting started ────────────────────────────────────
  {
    id: 'know-yourself',
    name: 'Know Yourself',
    description: 'You discovered your team-role profile.',
    criteria: 'Complete this year’s personality test.',
    category: 'getting-started',
    rarity: 'common',
    goal: 1,
    trigger: 'PersonalityTestCompleted',
    rule: 'Student has a Personality Profile for the current academic year.',
    tint: ['#efecff', '#d6ceff'],
  },
  {
    id: 'all-set',
    name: 'All Set',
    description: 'Your profile tells your story.',
    criteria: 'Add a banner, an about me, a skill, an interest and a language to your profile.',
    category: 'getting-started',
    rarity: 'common',
    goal: 5,
    unit: 'sections',
    trigger: 'ProfileUpdated',
    rule: 'banner ≠ null AND aboutMe ≠ "" AND skills ≥ 1 AND interests ≥ 1 AND languages ≥ 1.',
    tint: ['#e4f3ff', '#bfe3ff'],
  },
  {
    id: 'polyglot',
    name: 'Polyglot',
    description: 'You can team up in many languages.',
    criteria: 'List three or more languages on your profile.',
    category: 'getting-started',
    rarity: 'common',
    goal: 3,
    unit: 'languages',
    trigger: 'ProfileUpdated',
    rule: 'COUNT(profile.languages) ≥ 3.',
    tint: ['#ffeee4', '#ffd2bb'],
  },
  // ── Teamwork ───────────────────────────────────────────
  {
    id: 'team-player',
    name: 'Team Player',
    description: 'Your first team is official.',
    criteria: 'Be a member of your first published group.',
    category: 'teamwork',
    rarity: 'common',
    goal: 1,
    unit: 'group',
    trigger: 'GroupsPublished',
    rule: 'COUNT(published groups with the student) ≥ 1.',
    tint: ['#efecff', '#cfeee0'],
  },
  {
    id: 'squad-veteran',
    name: 'Squad Veteran',
    description: 'Teams are your natural habitat.',
    criteria: 'Be a member of 10 published groups.',
    category: 'teamwork',
    rarity: 'rare',
    goal: 10,
    unit: 'groups',
    trigger: 'GroupsPublished',
    rule: 'COUNT(published groups with the student) ≥ 10.',
    tint: ['#e1f8ee', '#b8edd5'],
  },
  {
    id: 'new-faces',
    name: 'New Faces',
    description: 'You’ve teamed up with lots of different people.',
    criteria: 'Share a published group with 20 different classmates.',
    category: 'teamwork',
    rarity: 'rare',
    goal: 20,
    unit: 'classmates',
    trigger: 'GroupsPublished',
    rule: 'COUNT(DISTINCT teammates across published groups) ≥ 20.',
    tint: ['#e4f3ff', '#d6ceff'],
  },
  // ── StudyRooms ─────────────────────────────────────────
  {
    id: 'door-opener',
    name: 'Door Opener',
    description: 'You walked into your first StudyRoom.',
    criteria: 'Attend a StudyRoom (attendance confirmed by the tutor).',
    category: 'studyrooms',
    rarity: 'common',
    goal: 1,
    unit: 'StudyRoom',
    trigger: 'StudyRoomAttendanceConfirmed',
    rule: 'COUNT(confirmed StudyRoom attendances) ≥ 1.',
    tint: ['#ffeee4', '#ffe0a8'],
  },
  {
    id: 'early-bird',
    name: 'Early Bird',
    description: 'Learning before the first coffee is cold.',
    criteria: 'Attend a StudyRoom that starts before 09:00.',
    category: 'studyrooms',
    rarity: 'rare',
    goal: 1,
    trigger: 'StudyRoomAttendanceConfirmed',
    rule: 'EXISTS confirmed attendance WHERE studyRoom.start (local time) < 09:00.',
    tint: ['#f7fbd8', '#ffe2c4'],
  },
  {
    id: 'night-owl',
    name: 'Night Owl',
    description: 'Your best ideas come after dark.',
    criteria: 'Attend a StudyRoom that starts at 20:00 or later.',
    category: 'studyrooms',
    rarity: 'rare',
    goal: 1,
    trigger: 'StudyRoomAttendanceConfirmed',
    rule: 'EXISTS confirmed attendance WHERE studyRoom.start (local time) ≥ 20:00.',
    tint: ['#2a2560', '#0d0b24'],
    dark: true,
  },
  {
    id: 'bookworm',
    name: 'Bookworm',
    description: 'StudyRooms are part of your routine.',
    criteria: 'Attend 10 StudyRooms in the same semester.',
    category: 'studyrooms',
    rarity: 'epic',
    goal: 10,
    unit: 'StudyRooms',
    trigger: 'StudyRoomAttendanceConfirmed',
    rule: 'MAX over semesters of COUNT(confirmed attendances in that semester) ≥ 10.',
    tint: ['#efecff', '#ffd9e8'],
  },
  // ── Tutoring ───────────────────────────────────────────
  {
    id: 'mentor',
    name: 'Mentor',
    description: 'You share what you know.',
    criteria: 'Get approved as a Tutor in a subject.',
    category: 'tutoring',
    rarity: 'rare',
    goal: 1,
    trigger: 'TutorApplicationApproved',
    rule: 'COUNT(approved Tutor Roles) ≥ 1.',
    tint: ['#f7fbd8', '#e8f59a'],
  },
  {
    id: 'full-house',
    name: 'Full House',
    description: 'Every seat in your room was taken.',
    criteria: 'Host a StudyRoom that reaches full capacity and takes place.',
    category: 'tutoring',
    rarity: 'epic',
    goal: 1,
    trigger: 'StudyRoomCompleted',
    rule: 'EXISTS hosted StudyRoom WHERE state = Completed AND bookings = capacity.',
    tint: ['#ffe9f2', '#ffc9df'],
  },
  {
    id: 'five-stars',
    name: 'Five Stars',
    description: 'Students love your sessions.',
    criteria: 'Receive ten 5-star ratings as a Tutor.',
    category: 'tutoring',
    rarity: 'epic',
    goal: 10,
    unit: '5-star ratings',
    trigger: 'TutorRated',
    rule: 'COUNT(ratings of the tutor WHERE stars = 5) ≥ 10.',
    tint: ['#fff6d6', '#ffe08a'],
  },
  {
    id: 'kind-words',
    name: 'Kind Words',
    description: 'Your feedback helps tutors grow.',
    criteria: 'Rate 5 tutors with a public comment.',
    category: 'tutoring',
    rarity: 'common',
    goal: 5,
    unit: 'ratings',
    trigger: 'TutorRated',
    rule: 'COUNT(ratings by the student WHERE comment ≠ "") ≥ 5.',
    tint: ['#ffe9f2', '#ffd6c8'],
  },
  // ── Academic ───────────────────────────────────────────
  {
    id: 'never-missed',
    name: 'Never Missed',
    description: 'Present, every single class.',
    criteria: 'Finish a subject with no unjustified absences.',
    category: 'academic',
    rarity: 'rare',
    goal: 1,
    trigger: 'FinalGradePublished',
    rule: 'EXISTS finished enrolment WHERE unjustified absences = 0 (students with a special statute are not eligible).',
    tint: ['#e1f8ee', '#c9f2ff'],
  },
  {
    id: 'top-ten',
    name: 'Top 10%',
    description: 'Among the best in your class.',
    criteria: 'Finish a subject in the top 10% of your class.',
    category: 'academic',
    rarity: 'epic',
    goal: 1,
    trigger: 'FinalGradePublished',
    rule: 'EXISTS enrolment WHERE status = Passed AND percentile within the class ≥ 90.',
    tint: ['#e4f3ff', '#c4d8ff'],
  },
  {
    id: 'clean-sweep',
    name: 'Clean Sweep',
    description: 'A whole semester without a single fail.',
    criteria: 'Pass every subject of a semester at the first attempt.',
    category: 'academic',
    rarity: 'epic',
    goal: 1,
    trigger: 'SemesterClosed',
    rule: 'EXISTS semester WHERE every enrolment is Passed AND is the first attempt (minimum 3 subjects).',
    tint: ['#fff6d6', '#ffd9a8'],
  },
  {
    id: 'category-master',
    name: 'Category Master',
    description: 'A true specialist in your field.',
    criteria: 'Reach the High level in a course category.',
    category: 'academic',
    rarity: 'legendary',
    goal: 1,
    trigger: 'FinalGradePublished',
    rule: 'EXISTS category WHERE the student’s level (GR3) = High.',
    tint: ['#efecff', '#ffe6a8'],
  },
  {
    id: 'perfect-score',
    name: 'Perfect 20',
    description: 'The highest grade there is.',
    criteria: 'Get a final grade of 20 in a subject.',
    category: 'academic',
    rarity: 'legendary',
    goal: 1,
    trigger: 'FinalGradePublished',
    rule: 'EXISTS enrolment WHERE finalGrade = 20.',
    tint: ['#e4f3ff', '#e9dcff'],
  },
];

export const ACHIEVEMENTS_BY_ID = Object.fromEntries(ACHIEVEMENTS.map((a) => [a.id, a])) as Record<AchievementId, AchievementDef>;

export function getAchievement(id: AchievementId | string): AchievementDef | undefined {
  return ACHIEVEMENTS_BY_ID[id as AchievementId];
}

/** Student-facing state of one achievement. */
export interface AchievementState {
  id: AchievementId;
  /** ISO date or Date when earned. Undefined = not earned yet. */
  earnedAt?: string | Date;
  /** Current count toward `goal` (e.g. 3 of 10). */
  progress?: number;
}
