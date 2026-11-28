import { useTranslation } from 'react-i18next';
import { PROFILE_KEYS, PROFILES } from '../../studymatch-ui';
import type { PersonalityProfileData } from '../../types/personalityProfile';

// a student's Big Five in a table row: five slim bars in the trait colours of the profile page, each with its own initial
// (in pt abertura and amabilidade both start with A, so they're Ab and Am),
// so the row stays one line tall. Every value is in the hover and, as text, for screen readers
export function PersonalitySummary({ profile }: { profile: PersonalityProfileData | null }) {
  const { t, i18n } = useTranslation();
  if (profile === null) return <span className="sm-caption">{t('directory.noPersonality')}</span>;
  const percent = new Intl.NumberFormat(i18n.language, { style: 'percent', maximumFractionDigits: 0 });
  const line = (key: typeof PROFILE_KEYS[number]) => `${t(`traits.${key}.label`)} ${percent.format(profile[key])}`;
  return (
    <div className="student-personality" title={PROFILE_KEYS.map(line).join('\n')}>
      <span className="student-personality__bars" aria-hidden>
        {PROFILE_KEYS.map(key => (
          <span key={key} className="student-personality__bar">
            <span className="student-personality__track"><i style={{ height: `${Math.round(profile[key] * 100)}%`, background: PROFILES[key].color }} /></span>
            <b>{t(`traits.${key}.initial`)}</b>
          </span>
        ))}
      </span>
      <dl className="sm-visually-hidden">{PROFILE_KEYS.map(key => <div key={key}><dt>{t(`traits.${key}.label`)}</dt><dd>{percent.format(profile[key])}</dd></div>)}</dl>
    </div>
  );
}
