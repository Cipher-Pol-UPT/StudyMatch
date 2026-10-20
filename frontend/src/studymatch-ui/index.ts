/**
 * StudyMatch UI
 * import { Button, StudyMatchProvider } from './studymatch-ui';
 */
import './styles/base.css';
import './styles/fonts.css';
import './styles/tokens.css';
import './styles/themes.css';

export { StudyMatchProvider,type StudyMatchProviderProps } from './Provider';
export { ThemeProvider,type ThemeProviderProps } from './themes/ThemeProvider';
export { useTheme } from './themes/ThemeContext';
export { THEMES,DEFAULT_THEME,THEME_STORAGE_KEY,isThemeId,type ThemeId } from './themes/themes';
export { ThemeSelector } from './components/theme/ThemeSelector';
export { ThemeSwitcher } from './components/theme/ThemeSwitcher';

// Utilities
export { cx } from './utils/cx';
export { checkPassword,type PasswordRule,type PasswordRuleResult } from './utils/password';
export { useFormFeedback } from './utils/useFormFeedback';
export { EASE_OUT,SPRING,formatBytes,hashString,useControllableState,type IconComponent } from './utils/shared';

// Brand
export { Logo,Mark,type LogoProps,type MarkProps } from './components/brand/Logo';

// Actions
export { Button,ButtonGroup,IconButton,type ButtonProps,type ButtonSize,type ButtonVariant,type IconButtonProps } from './components/button/Button';
export { buttonClass } from './components/button/Button.shared';

// Inputs
export { Calendar,type CalendarEvent,type CalendarProps } from './components/calendar/Calendar';
export { Chip,ChipGroup,type ChipProps } from './components/chip/Chip';
export { TagInput,type TagInputProps } from './components/chip/TagInput';
export { Field,type FieldShellProps } from './components/field/Field';
export { PasswordField,type PasswordFieldProps } from './components/field/PasswordField';
export { SearchField,type SearchFieldProps } from './components/field/SearchField';
export { Select,type SelectOption,type SelectProps } from './components/field/Select';
export { Textarea,type TextareaProps } from './components/field/Textarea';
export { TextField,type TextFieldProps } from './components/field/TextField';
export { CodeInput,type CodeInputProps } from './components/inputs/CodeInput';
export { FileDrop,type FileDropProps } from './components/inputs/FileDrop';
export { NumberStepper,type NumberStepperProps } from './components/inputs/NumberStepper';
export { Rating,type RatingProps } from './components/rating/Rating';
export { Segmented,type SegmentedOption,type SegmentedProps } from './components/segmented/Segmented';
export { Tabs,type TabItem,type TabsProps } from './components/segmented/Tabs';
export { tabPanelProps } from './components/segmented/Tabs.shared';
export { Checkbox,type CheckboxProps } from './components/selection/Checkbox';
export { Radio,RadioGroup,type RadioGroupProps,type RadioProps } from './components/selection/Radio';
export { ControlRow,Switch,Toggle,type ControlRowProps,type SwitchProps } from './components/selection/Switch';

// Display
export { Accordion,type AccordionItem,type AccordionProps } from './components/accordion/Accordion';
export { Avatar,AvatarStack,type AvatarProps,type AvatarStackProps } from './components/avatar/Avatar';
export { AVATAR_GRADIENTS,initialsOf } from './components/avatar/Avatar.shared';
export { Badge,DomainStatus,ProfileDot,ProfileTag,StatusPill,type BadgeProps,type ProfileKey,type ProfileTagProps,type StatusPillProps,type StatusTone } from './components/badge/Badge';
export { DOMAIN_STATUS,PROFILES,PROFILE_KEYS } from './components/badge/Badge.shared';
export { Card,StatCard,type CardProps,type CardTone,type StatCardProps } from './components/card/Card';
export { isDarkTone } from './components/card/Card.shared';
export { BarChart,DonutChart,ProfileBars,type BarChartProps,type BarDatum,type DonutChartProps,type DonutSegment,type ProfileBarsProps } from './components/charts/Charts';
export { EmptyState,type EmptyStateProps } from './components/empty/EmptyState';
export { IconTile,List,ListRow,type IconTileProps,type ListProps,type ListRowProps } from './components/list/List';
export { ProgressBar,ScoreRing,Skeleton,Spinner,type ProgressBarProps,type ScoreRingProps,type SkeletonProps,type SpinnerProps } from './components/progress/Progress';
export { Steps,type StepItem,type StepsProps } from './components/steps/Steps';
export { Pagination,type PaginationProps } from './components/table/Pagination';
export { paginationRange } from './components/table/Pagination.shared';
export { Table,type SortState,type TableColumn,type TableProps } from './components/table/Table';

// Feedback & overlays
export { NotificationBell,NotificationList,type NotificationBellProps,type NotificationItem,type NotificationListProps } from './components/notification/Notifications';
export { Banner,type BannerProps,type BannerTone } from './components/overlay/Banner';
export { ConfirmDialog,Dialog,Sheet,type ConfirmDialogProps,type DialogProps,type SheetProps } from './components/overlay/Dialog';
export { Menu,type MenuEntry,type MenuProps } from './components/overlay/Menu';
export { Popover,type PopoverProps } from './components/overlay/Popover';
export { Toast,ToastProvider,type ToastFn,type ToastOptions,type ToastProps,type ToastTone } from './components/overlay/Toast';
export { Tooltip,type TooltipProps } from './components/overlay/Tooltip';
export { useModal } from './components/overlay/useModal';
export { useToast } from './components/overlay/useToast';

// Navigation
export { AppShell,Breadcrumbs,TabBar,type AppShellProps,type BreadcrumbsProps,type NavItem,type NavSection,type TabBarItem,type TabBarProps } from './components/navigation/Navigation';

// Expressive
export { Amount,GroupSizePad,Keypad,type AmountProps,type GroupSizePadProps,type KeypadProps } from './components/expressive/Amount';
export { GroupTile,type GroupTileProps } from './components/expressive/GroupTile';
export { StudentPass,type StudentPassProps } from './components/expressive/StudentPass';
export { StickyBoard,StickyNote,type NoteColor,type NoteData,type StickyNoteProps } from './components/sticky-note/StickyNote';
export { NOTE_COLORS,NOTE_HEX,tiltFor } from './components/sticky-note/StickyNote.shared';

// Achievements
export {
AchievementBadge,AchievementCard,AchievementCertificate,AchievementDialog,AchievementGrid,AchievementTile,AchievementUnlocked,
type AchievementBadgeProps,type AchievementCardProps,type AchievementCertificateProps,type AchievementDialogProps,
type AchievementGridProps,type AchievementTileProps,type AchievementUnlockedProps
} from './achievements/Achievement';
export { AchievementArt,type AchievementArtProps } from './achievements/AchievementArt';
export { ACHIEVEMENT_ART,type AchievementId,type AchievementState } from './achievements/catalog';

// Blocks
export { BlockAccountDialog,CreateAccountForm,RegisterStudentForm,type BlockAccountDialogProps,type CreateAccountFormProps,type RegisterStudentFormProps } from './blocks/accounts/AccountForms';
export { ActivateAccount,type ActivateAccountProps } from './blocks/auth/ActivateAccount';
export { AuthHeader,AuthLayout,type AuthHeaderProps,type AuthLayoutProps } from './blocks/auth/AuthLayout';
export { LoginForm,type LoginFormProps } from './blocks/auth/LoginForm';
export { ChangePasswordForm,ForgotPasswordForm,ResetPasswordForm,type ChangePasswordFormProps,type ForgotPasswordFormProps,type ResetPasswordFormProps } from './blocks/auth/PasswordForms';
export { AuthStatus,BlockedNotice,CheckEmail,VerifyEmail,type AuthStatusProps,type BlockedNoticeProps,type CheckEmailProps,type VerifyEmailProps } from './blocks/auth/StatusScreens';
export { PersonalityTest,type PersonalityQuestion,type PersonalityTestProps } from './blocks/personality/PersonalityTest';
export { EditProfileForm,type EditProfileFormProps,type ProfileEdits } from './blocks/profile/EditProfileForm';
export { ImageCropper,type CroppedImage,type ImageCropperProps } from './blocks/profile/ImageCropper';
export { AvatarUploader,BannerUploader,ProfileBanner,type AvatarUploaderProps,type BannerPreset,type BannerUploaderProps,type ProfileBannerProps } from './blocks/profile/ProfileMedia';
export { BANNER_PRESETS,validateProfileImage } from './blocks/profile/ProfileMedia.shared';
export { ProfileCard,ProfileHeader,ProfileView,type CategoryLevel,type ProfileHeaderProps,type ProfileViewProps,type ProfileViewer,type StudentProfile } from './blocks/profile/ProfileView';
export { LEVEL } from './blocks/profile/ProfileView.shared';
export { formatRoomDate,type StudyRoom,type StudyRoomActions,type StudyRoomPerson,type StudyRoomStatus } from './blocks/studyroom/model';
export {
ConfirmAttendanceDialog,
CreateStudyRoomForm,
StudyRoomBoard,
StudyRoomDetails,
type CreateStudyRoomFormProps,
type StudyRoomBoardProps,
type StudyRoomDetailsProps,
type StudyRoomDraft
} from './blocks/studyroom/StudyRoomBoard';
export { MeetButton,StudyRoomCard,StudyRoomStatusPill,type MeetButtonProps,type StudyRoomCardProps } from './blocks/studyroom/StudyRoomCard';
