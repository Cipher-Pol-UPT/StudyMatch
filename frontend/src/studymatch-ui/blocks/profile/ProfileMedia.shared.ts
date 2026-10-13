import i18n from '../../../i18n';
import { formatBytes } from '../../utils/shared';
import './profile.css';
import type { BannerPreset } from './ProfileMedia';

export function validateProfileImage(file: File, maxSize = DEFAULT_IMAGE_MAX): string | null {
  if (!PROFILE_IMAGE_TYPES.includes(file.type)) return i18n.t('ui.use_a_jpg_or_png_image');
  if (file.size > maxSize) return i18n.t('ui.image_size', {size:formatBytes(file.size),max:formatBytes(maxSize)});
  return null;
}

export const BANNER_PRESETS: Record<BannerPreset, { label: string; background: string }> = {
  violet: { get label() { return i18n.t('ui.violet'); }, background: 'radial-gradient(60% 120% at 85% 0%, rgba(255,255,255,.28), transparent 60%), var(--sm-grad-brand)' },
  sunrise: { get label() { return i18n.t('ui.sunrise'); }, background: 'radial-gradient(60% 120% at 85% 0%, rgba(255,255,255,.3), transparent 60%), var(--sm-grad-sunrise)' },
  fresh: { get label() { return i18n.t('ui.fresh'); }, background: 'radial-gradient(60% 120% at 85% 0%, rgba(255,255,255,.3), transparent 60%), var(--sm-grad-fresh)' },
  ink: { get label() { return i18n.t('ui.ink'); }, background: 'radial-gradient(50% 120% at 90% 0%, rgba(122,107,255,.7), transparent 70%), radial-gradient(40% 100% at 0% 100%, rgba(42,159,214,.45), transparent 70%), #0d0b24' },
  lavender: { get label() { return i18n.t('ui.lavender'); }, background: 'radial-gradient(60% 120% at 90% 0%, #d6ceff, transparent 70%), var(--sm-tint-lavender)' },
  sky: { get label() { return i18n.t('ui.sky'); }, background: 'radial-gradient(60% 120% at 90% 0%, #bfe3ff, transparent 70%), var(--sm-tint-sky)' },
  peach: { get label() { return i18n.t('ui.peach'); }, background: 'radial-gradient(60% 120% at 90% 0%, #ffd2bb, transparent 70%), var(--sm-tint-peach)' },
  mint: { get label() { return i18n.t('ui.mint'); }, background: 'radial-gradient(60% 120% at 90% 0%, #b8edd5, transparent 70%), var(--sm-tint-mint)' },
};

export const PROFILE_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export const DEFAULT_IMAGE_MAX = Infinity;
