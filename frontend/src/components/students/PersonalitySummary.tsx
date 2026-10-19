import { useTranslation } from 'react-i18next';
import { PROFILE_KEYS } from '../../studymatch-ui';
import type { PersonalityProfileData } from '../../types/personalityProfile';

export function PersonalitySummary({ profile }: { profile: PersonalityProfileData | null }) {
  const { t, i18n } = useTranslation();
  if (profile === null) return <span className="sm-caption">{t('directory.noPersonality')}</span>;
  const percent = new Intl.NumberFormat(i18n.language, { style: 'percent', maximumFractionDigits: 0 });
  return <dl className="student-personality">{PROFILE_KEYS.map(key => <div key={key}><dt>{t(`directory.traits.${key}`)}</dt><dd>{percent.format(profile[key])}</dd></div>)}</dl>;
}
