export const AccountStatus = ["ACTIVE", "BLOCKED"] as const;
export type AccountStatus = typeof AccountStatus[number];

export const Achievement = ["FIRST_STUDYROOM", "DEDICATED_TUTOR", "TOP_RATED", "MENTOR"] as const;
export type Achievement = typeof Achievement[number];

export const AssignmentType = ["PROJECT", "EXAM", "PRACTICAL_EXAM"] as const;
export type AssignmentType = typeof AssignmentType[number];

export const EnrolmentStatus = ["IN_PROGRESS", "PASSED", "FAILED"] as const;
export type EnrolmentStatus = typeof EnrolmentStatus[number];

export const GroupStatus = ["DRAFT", "PUBLISHED"] as const;
export type GroupStatus = typeof GroupStatus[number];

export const Language = ["PORTUGUESE", "ENGLISH", "SPANISH", "FRENCH", "GERMAN", "ITALIAN", "DUTCH", "RUSSIAN", "UKRAINIAN", "ARABIC", "HINDI", "MANDARIN", "JAPANESE", "KOREAN"] as const;
export type Language = typeof Language[number];

export const StatuteType = ["WORKING_STUDENT", "HIGH_PERFORMANCE_ATHLETE", "STUDENT_ASSOCIATION_LEADER", "STUDENT_PARENT", "SPECIAL_NEEDS"] as const;
export type StatuteType = typeof StatuteType[number];

export const StudyRoomStatus = ["SCHEDULED", "FULL", "CANCELLED", "COMPLETED"] as const;
export type StudyRoomStatus = typeof StudyRoomStatus[number];

export const TutorApplicationStatus = ["PENDING", "APPROVED", "REJECTED", "CANCELLED"] as const;
export type TutorApplicationStatus = typeof TutorApplicationStatus[number];

export const Role = ["ADMIN", "STAFF", "TEACHER", "STUDENT"] as const;
export type Role = typeof Role[number];

export const ChatEventType = ["MESSAGE_CREATED", "MESSAGE_EDITED", "MESSAGE_DELETED", "CHAT_UPDATED", "CHAT_ADDED", "CHAT_READ", "CHAT_REMOVED", "TYPING", "USER_ONLINE", "USER_OFFLINE"] as const;
export type ChatEventType = typeof ChatEventType[number];

export const NotificationLevel = ["ALL", "MENTIONS", "NONE"] as const;
export type NotificationLevel = typeof NotificationLevel[number];
