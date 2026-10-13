/**
 * StudyMatch UI
 * import { Button, StudyMatchProvider } from './studymatch-ui';
 */
import './styles/fonts.css';
import './styles/tokens.css';
import './styles/base.css';

export { StudyMatchProvider, type StudyMatchProviderProps } from './Provider';

// Utilities
export { cx } from './utils/cx';
export { EASE_OUT, SPRING, formatBytes, hashString, useControllableState, type IconComponent } from './utils/shared';
export {
  checkPassword,
  isPasswordValid,
  passwordStrength,
  STRENGTH_LABELS,
  STUDYMATCH_PASSWORD_RULES,
  type PasswordRule,
  type PasswordRuleResult,
  type PasswordStrength,
} from './utils/password';

// Brand
export { Logo, Mark, type LogoProps, type MarkProps } from './components/brand/Logo';

// Actions
export { Button, ButtonGroup, IconButton, buttonClass, type ButtonProps, type ButtonSize, type ButtonVariant, type IconButtonProps } from './components/button/Button';

// Inputs
export { Field, type FieldShellProps } from './components/field/Field';
export { TextField, type TextFieldProps } from './components/field/TextField';
export { PasswordField, type PasswordFieldProps } from './components/field/PasswordField';
export { Textarea, type TextareaProps } from './components/field/Textarea';
export { Select, type SelectOption, type SelectProps } from './components/field/Select';
export { SearchField, type SearchFieldProps } from './components/field/SearchField';
export { Checkbox, type CheckboxProps } from './components/selection/Checkbox';
export { Radio, RadioGroup, type RadioGroupProps, type RadioProps } from './components/selection/Radio';
export { ControlRow, Switch, Toggle, type ControlRowProps, type SwitchProps } from './components/selection/Switch';
export { Segmented, type SegmentedOption, type SegmentedProps } from './components/segmented/Segmented';
export { Tabs, tabPanelProps, type TabItem, type TabsProps } from './components/segmented/Tabs';
export { Chip, ChipGroup, type ChipProps } from './components/chip/Chip';
export { TagInput, type TagInputProps } from './components/chip/TagInput';
export { CodeInput, type CodeInputProps } from './components/inputs/CodeInput';
export { NumberStepper, type NumberStepperProps } from './components/inputs/NumberStepper';
export { FileDrop, type FileDropProps } from './components/inputs/FileDrop';
export { Rating, type RatingProps } from './components/rating/Rating';
export { Calendar, type CalendarEvent, type CalendarProps } from './components/calendar/Calendar';

// Display
export {
  Badge,
  DOMAIN_STATUS,
  DomainStatus,
  PROFILE_KEYS,
  PROFILES,
  ProfileDot,
  ProfileTag,
  StatusPill,
  type BadgeProps,
  type ProfileKey,
  type ProfileTagProps,
  type StatusPillProps,
  type StatusTone,
} from './components/badge/Badge';
export { AVATAR_GRADIENTS, Avatar, AvatarStack, initialsOf, type AvatarProps, type AvatarStackProps } from './components/avatar/Avatar';
export { IconTile, List, ListRow, type IconTileProps, type ListProps, type ListRowProps } from './components/list/List';
export { Card, StatCard, isDarkTone, type CardProps, type CardTone, type StatCardProps } from './components/card/Card';
export { Table, type SortState, type TableColumn, type TableProps } from './components/table/Table';
export { Pagination, paginationRange, type PaginationProps } from './components/table/Pagination';
export { ProgressBar, ScoreRing, Skeleton, Spinner, type ProgressBarProps, type ScoreRingProps, type SkeletonProps, type SpinnerProps } from './components/progress/Progress';
export { EmptyState, type EmptyStateProps } from './components/empty/EmptyState';
export { Steps, type StepItem, type StepsProps } from './components/steps/Steps';
export { Accordion, type AccordionItem, type AccordionProps } from './components/accordion/Accordion';
export { BarChart, DonutChart, ProfileBars, type BarChartProps, type BarDatum, type DonutChartProps, type DonutSegment, type ProfileBarsProps } from './components/charts/Charts';

// Feedback & overlays
export { Banner, type BannerProps, type BannerTone } from './components/overlay/Banner';
export { Toast, ToastProvider, useToast, type ToastFn, type ToastOptions, type ToastProps, type ToastTone } from './components/overlay/Toast';
export { ConfirmDialog, Dialog, Sheet, type ConfirmDialogProps, type DialogProps, type SheetProps } from './components/overlay/Dialog';
export { Tooltip, type TooltipProps } from './components/overlay/Tooltip';
export { Popover, type PopoverProps } from './components/overlay/Popover';
export { Menu, type MenuEntry, type MenuProps } from './components/overlay/Menu';
export { useModal } from './components/overlay/useModal';
export { NotificationBell, NotificationList, type NotificationBellProps, type NotificationItem, type NotificationListProps } from './components/notification/Notifications';

// Navigation
export { AppShell, Breadcrumbs, TabBar, type AppShellProps, type BreadcrumbsProps, type NavItem, type NavSection, type TabBarItem, type TabBarProps } from './components/navigation/Navigation';

// Expressive
export { Amount, GroupSizePad, Keypad, type AmountProps, type GroupSizePadProps, type KeypadProps } from './components/expressive/Amount';
export { StudentPass, type StudentPassProps } from './components/expressive/StudentPass';
export { GroupTile, type GroupTileProps } from './components/expressive/GroupTile';
export { NOTE_COLORS, NOTE_HEX, StickyBoard, StickyNote, tiltFor, type NoteColor, type NoteData, type StickyNoteProps } from './components/sticky-note/StickyNote';

// Achievements
export {
  ACHIEVEMENT_CATEGORIES,
  ACHIEVEMENTS,
  ACHIEVEMENTS_BY_ID,
  getAchievement,
  RARITY,
  type AchievementCategory,
  type AchievementDef,
  type AchievementId,
  type AchievementRarity,
  type AchievementState,
} from './achievements/catalog';
export { AchievementArt, type AchievementArtProps } from './achievements/AchievementArt';
export {
  AchievementBadge,
  AchievementCard,
  AchievementCertificate,
  AchievementDialog,
  AchievementGrid,
  AchievementTile,
  AchievementUnlocked,
  RarityPill,
  achievementStatus,
  type AchievementBadgeProps,
  type AchievementCardProps,
  type AchievementCertificateProps,
  type AchievementDialogProps,
  type AchievementGridProps,
  type AchievementStatus,
  type AchievementTileProps,
  type AchievementUnlockedProps,
} from './achievements/Achievement';

// Auth
export * from './auth/types';
export { AuthProvider, RequireAuth, useAuth, useOptionalAuth, type AuthContextValue, type AuthProviderProps, type RequireAuthProps } from './auth/AuthProvider';
export { MOCK_USERS, createMockAuthAdapter, type MockAuthAdapter, type MockAuthOptions, type MockEmail, type MockUser } from './auth/mockAdapter';
export { createHttpAuthAdapter, type HttpAuthOptions } from './auth/httpAdapter';

// Blocks
export { AuthHeader, AuthLayout, type AuthHeaderProps, type AuthLayoutProps } from './blocks/auth/AuthLayout';
export { LoginForm, isEmail, type DemoAccount, type LoginFormProps } from './blocks/auth/LoginForm';
export { ChangePasswordForm, ForgotPasswordForm, ResetPasswordForm, type ChangePasswordFormProps, type ForgotPasswordFormProps, type ResetPasswordFormProps } from './blocks/auth/PasswordForms';
export { ActivateAccount, type ActivateAccountProps } from './blocks/auth/ActivateAccount';
export { AuthStatus, BlockedNotice, CheckEmail, VerifyEmail, type AuthStatusProps, type BlockedNoticeProps, type CheckEmailProps, type VerifyEmailProps } from './blocks/auth/StatusScreens';
export { BlockAccountDialog, CreateAccountForm, RegisterStudentForm, type BlockAccountDialogProps, type CreateAccountFormProps, type RegisterStudentFormProps } from './blocks/accounts/AccountForms';
export {
  DEFAULT_PERSONALITY_QUESTIONS,
  LIKERT,
  PersonalityTest,
  scorePersonality,
  type PersonalityQuestion,
  type PersonalityResult,
  type PersonalityTestProps,
} from './blocks/personality/PersonalityTest';
export { ImageCropper, type CroppedImage, type ImageCropperProps } from './blocks/profile/ImageCropper';
export {
  AvatarUploader,
  BANNER_PRESETS,
  BannerUploader,
  PROFILE_IMAGE_MAX,
  PROFILE_IMAGE_TYPES,
  ProfileBanner,
  validateProfileImage,
  type AvatarUploaderProps,
  type BannerPreset,
  type BannerUploaderProps,
  type ProfileBannerProps,
} from './blocks/profile/ProfileMedia';
export {
  LEVEL,
  ProfileCard,
  ProfileHeader,
  ProfileView,
  type CategoryLevel,
  type ProfileHeaderProps,
  type ProfileViewer,
  type ProfileViewProps,
  type StudentProfile,
} from './blocks/profile/ProfileView';
export { EditProfileForm, type EditProfileFormProps, type ProfileEdits } from './blocks/profile/EditProfileForm';
export {
  CAPACITY_RANGE,
  ENTER_EARLY_MIN,
  MEET_URL_PATTERN,
  fakeMeetUrl,
  formatCountdown,
  studyRoomAccess,
  studyRoomPhase,
  useNow,
  type StudyRoom,
  type StudyRoomAccess,
  type StudyRoomPerson,
  type StudyRoomPhase,
  type StudyRoomStatus,
} from './blocks/studyroom/model';
export { MeetButton, StudyRoomCard, StudyRoomStatusPill, type MeetButtonProps, type StudyRoomCardProps } from './blocks/studyroom/StudyRoomCard';
export {
  ConfirmAttendanceDialog,
  CreateStudyRoomForm,
  StudyRoomBoard,
  StudyRoomDetails,
  type CreateStudyRoomFormProps,
  type StudyRoomBoardProps,
  type StudyRoomDetailsProps,
  type StudyRoomDraft,
  type StudyRoomUser,
} from './blocks/studyroom/StudyRoomBoard';
