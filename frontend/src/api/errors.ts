export const ERROR_CODES = [
  "VALIDATION_FAILED",
  "NOT_FOUND",
  "UNAUTHENTICATED",
  "INVALID_CREDENTIALS",
  "ACCOUNT_BLOCKED",
  "ACCESS_DENIED",
  "CSRF_TOKEN_INVALID",
  "PASSWORD_CHANGE_REQUIRED",
  "PERSONALITY_TEST_REQUIRED",
  "PERSONALITY_TEST_ALREADY_TAKEN",
  "CURRENT_PASSWORD_INCORRECT",
  "NEW_PASSWORD_SAME_AS_CURRENT",
  "PASSWORD_TOO_LONG",
  "RESET_CODE_INVALID",
  "INSTITUTIONAL_EMAIL_ALREADY_EXISTS",
  "PERSONAL_EMAIL_ALREADY_EXISTS",
  "ACCOUNT_TYPE_NOT_ALLOWED",
  "EMAIL_NOT_SENT",
  "AT_LEAST_ONE_LANGUAGE_REQUIRED",
  "INVALID_BANNER",
  "STATUTE_END_DATE_IN_PAST",
  "SUBJECT_ALREADY_IN_DEGREE",
  "DEGREE_ALREADY_EXISTS",
  "AT_LEAST_ONE_TEACHER_REQUIRED",
  "CLASS_SECTION_ALREADY_EXISTS",
  "CLASS_SECTION_NOT_IN_SUBJECT_EDITION",
  "GROUP_WORK_NOT_ALLOWED_FOR_EXAMS",
  "INVALID_GROUP_SIZES",
  "SEPARATION_SAME_STUDENT",
  "SEPARATION_ALREADY_EXISTS",
  "ASSIGNMENT_NOT_GROUP_WORK",
  "APPLICATION_ALREADY_DECIDED",
  "STUDYROOM_CLOSED",
  "STUDYROOM_ALREADY_STARTED",
  "STUDYROOM_IN_PAST",
  "STUDYROOM_ENDS_BEFORE_START",
  "STUDYROOM_OVERLAP",
  "STUDYROOM_NOT_ENDED",
  "STUDYROOM_FULL",
  "TUTOR_CANNOT_JOIN_OWN_STUDYROOM",
  "ALREADY_JOINED_STUDYROOM",
  "NOT_A_PARTICIPANT",
  "CAPACITY_BELOW_PARTICIPANTS",
  "ATTENDANCE_NOT_CONFIRMED",
  "RATING_WINDOW_CLOSED",
] as const;
export type ErrorCode = typeof ERROR_CODES[number];
export type ErrorParams = Record<string, unknown>;
export interface FieldError { field: string; code: string; params?: ErrorParams }
export interface BackendError { code: ErrorCode; params?: ErrorParams; fieldErrors?: FieldError[] }

const record = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

export function isBackendError(value: unknown): value is BackendError {
  if (!record(value) || !ERROR_CODES.some(code => code === value.code)) return false;
  if (value.params !== undefined && !record(value.params)) return false;
  return value.fieldErrors === undefined || (Array.isArray(value.fieldErrors) && value.fieldErrors.every(
    field => record(field) && typeof field.field === 'string' && typeof field.code === 'string' &&
      /^[A-Z][A-Z_]*$/.test(field.code) && (field.params === undefined || record(field.params)),
  ));
}
