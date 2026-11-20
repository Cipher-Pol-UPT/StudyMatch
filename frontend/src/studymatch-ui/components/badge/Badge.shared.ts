import { Activity,HeartHandshake,Lightbulb,Megaphone,Target } from 'lucide-react';
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

// each trait keeps one of the identity colours; neuroticism gets a neutral one
function trait(key: ProfileKey, color: string, icon: IconComponent) {
  return {
    get label() { return i18n.t(`traits.${key}.label`); },
    get short() { return i18n.t(`traits.${key}.short`); },
    color,
    icon,
    get description() { return i18n.t(`traits.${key}.description`); },
  };
}

export const PROFILES: Record<ProfileKey, { label: string; short: string; color: string; icon: IconComponent; description: string }> = {
  openness: trait('openness', 'var(--sm-creative)', Lightbulb),
  conscientiousness: trait('conscientiousness', 'var(--sm-exec)', Target),
  extraversion: trait('extraversion', 'var(--sm-lead)', Megaphone),
  agreeableness: trait('agreeableness', 'var(--sm-tech)', HeartHandshake),
  neuroticism: trait('neuroticism', 'var(--sm-neuro)', Activity),
};

export const PROFILE_KEYS = Object.keys(PROFILES) as ProfileKey[];
