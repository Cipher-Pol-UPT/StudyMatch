export const AccountStatus = ["ACTIVE", "BLOCKED"] as const;
export type AccountStatus = typeof AccountStatus[number];

export const Achievement = ["FIRST_STUDYROOM", "DEDICATED_TUTOR", "TOP_RATED", "MENTOR"] as const;
export type Achievement = typeof Achievement[number];

export const AssignmentType = ["PROJECT", "EXAM", "PRACTICAL_EXAM"] as const;
export type AssignmentType = typeof AssignmentType[number];

export const EnrolmentStatus = ["IN_PROGRESS", "PASSED", "FAILED"] as const;
export type EnrolmentStatus = typeof EnrolmentStatus[number];

export const ExportFormat = ["CSV", "PDF"] as const;
export type ExportFormat = typeof ExportFormat[number];

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

export const ChatEventType = ["MESSAGE_CREATED", "MESSAGE_EDITED", "MESSAGE_DELETED", "MESSAGE_REACTED", "CHAT_UPDATED", "CHAT_EDITED", "CHAT_ADDED", "CHAT_READ", "CHAT_REMOVED", "TYPING", "USER_ONLINE", "USER_OFFLINE"] as const;
export type ChatEventType = typeof ChatEventType[number];

export const NotificationLevel = ["ALL", "MENTIONS", "NONE"] as const;
export type NotificationLevel = typeof NotificationLevel[number];

export const NotificationType = ["ACCOUNT_UNBLOCKED", "TUTOR_APPLICATION_SUBMITTED", "TUTOR_APPLICATION_APPROVED", "TUTOR_APPLICATION_REJECTED", "STUDYROOM_LEFT", "STUDYROOM_UPDATED", "STUDYROOM_CANCELLED", "STUDYROOM_RATING_OPEN", "ACHIEVEMENT_EARNED", "GROUP_PUBLISHED", "GROUP_UPDATED"] as const;
export type NotificationType = typeof NotificationType[number];

export const NotificationTarget = ["STUDYROOM", "ASSIGNMENT", "TUTOR_APPLICATION", "ACHIEVEMENT"] as const;
export type NotificationTarget = typeof NotificationTarget[number];

export const NoteColour = ["YELLOW", "PINK", "BLUE", "GREEN"] as const;
export type NoteColour = typeof NoteColour[number];

export const RoomType = ["CLASSROOM", "LAB", "AUDITORIUM"] as const;
export type RoomType = typeof RoomType[number];

export const TicketCategory = ["ACCOUNT", "GRADES_AND_ENROLMENT", "TECHNICAL", "OTHER"] as const;
export type TicketCategory = typeof TicketCategory[number];

export const TicketStatus = ["OPEN", "IN_PROGRESS", "RESOLVED"] as const;
export type TicketStatus = typeof TicketStatus[number];

export const Sticker = ["HELLO", "THANK_YOU", "ON_IT", "COFFEE_BREAK", "EXAM_PANIC", "ALL_NIGHTER", "PASSED", "GRADUATED", "SANTA", "SNOWMAN", "GIFT", "REINDEER", "BUNNY", "HATCHING", "EGG_HUNT", "CHOCOLATE", "GHOST", "PUMPKIN", "WITCH", "VAMPIRE", "SUNGLASSES", "ICE_CREAM", "POOL_FLOAT", "SARDINE", "LAUGHING", "MIND_BLOWN", "PARTY", "LOVE"] as const;
export type Sticker = typeof Sticker[number];
