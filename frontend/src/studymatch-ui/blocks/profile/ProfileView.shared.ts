import i18n from '../../../i18n';
import './profile.css';
import type { CategoryLevel } from './ProfileView';

export const LEVEL: Record<CategoryLevel, { label: string; tone: 'success' | 'info' | 'violet' }> = {
  high: { get label() { return i18n.t('ui.high'); }, tone: 'success' },
  average: { get label() { return i18n.t('ui.average'); }, tone: 'info' },
  developing: { get label() { return i18n.t('ui.developing'); }, tone: 'violet' },
};
