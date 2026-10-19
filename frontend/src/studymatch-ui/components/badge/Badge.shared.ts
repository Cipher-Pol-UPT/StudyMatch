import { Compass,Cpu,Lightbulb,Target } from 'lucide-react';
import i18n from '../../../i18n';
import type { IconComponent } from '../../utils/shared';
import type { ProfileKey,StatusTone } from './Badge';
import './badge.css';

export const DOMAIN_STATUS: Record<string, { tone: StatusTone; label: string }> = {
  DRAFT: { tone: 'warning', get label() { return i18n.t('groupStatuses.DRAFT'); } },
  PUBLISHED: { tone: 'success', get label() { return i18n.t('groupStatuses.PUBLISHED'); } },
  IN_PROGRESS: { tone: 'violet', get label() { return i18n.t('enrolmentStatuses.IN_PROGRESS'); } },
  PASSED: { tone: 'success', get label() { return i18n.t('enrolmentStatuses.PASSED'); } },
  FAILED: { tone: 'danger', get label() { return i18n.t('enrolmentStatuses.FAILED'); } },
  SCHEDULED: { tone: 'info', get label() { return i18n.t('studyRoomStatuses.SCHEDULED'); } },
  FULL: { tone: 'warning', get label() { return i18n.t('studyRoomStatuses.FULL'); } },
  COMPLETED: { tone: 'neutral', get label() { return i18n.t('studyRoomStatuses.COMPLETED'); } },
  CANCELLED: { tone: 'danger', get label() { return i18n.t('studyRoomStatuses.CANCELLED'); } },
  ACTIVE: { tone: 'success', get label() { return i18n.t('accountStatuses.ACTIVE'); } },
  BLOCKED: { tone: 'danger', get label() { return i18n.t('accountStatuses.BLOCKED'); } },
  PENDING: { tone: 'warning', get label() { return i18n.t('tutorApplicationStatuses.PENDING'); } },
  APPROVED: { tone: 'success', get label() { return i18n.t('tutorApplicationStatuses.APPROVED'); } },
  REJECTED: { tone: 'danger', get label() { return i18n.t('tutorApplicationStatuses.REJECTED'); } },
};

export const PROFILES: Record<ProfileKey, { label: string; short: string; color: string; icon: IconComponent; description: string }> = {
  leadership: { get label() { return i18n.t('ui.leadership'); }, get short() { return i18n.t('ui.lead'); }, color: 'var(--sm-lead)', icon: Compass, get description() { return i18n.t('ui.sets_direction_organises_the_team_and_keeps_everyone_aligned'); } },
  problemSolving: { get label() { return i18n.t('ui.problem_solving'); }, get short() { return i18n.t('ui.tech'); }, color: 'var(--sm-tech)', icon: Cpu, get description() { return i18n.t('ui.breaks_problems_down_and_finds_the_technical_path_forward'); } },
  creativity: { get label() { return i18n.t('ui.creativity'); }, get short() { return i18n.t('ui.creative'); }, color: 'var(--sm-creative)', icon: Lightbulb, get description() { return i18n.t('ui.brings_new_ideas_alternatives_and_a_fresh_point_of_view'); } },
  execution: { get label() { return i18n.t('ui.execution'); }, get short() { return i18n.t('ui.exec'); }, color: 'var(--sm-exec)', icon: Target, get description() { return i18n.t('ui.turns_plans_into_finished_work_on_time_and_in_detail'); } },
};

export const PROFILE_KEYS = Object.keys(PROFILES) as ProfileKey[];
