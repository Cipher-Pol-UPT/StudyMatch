import { CircleAlert, RefreshCw, ShieldCheck, ShieldX } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { verifyCertificate } from '../../api/certificates';
import { isBackendError } from '../../api/errors';
import { AchievementBadge, AuthHeader, AuthLayout, AuthStatus, Button, Spinner, ThemeSwitcher } from '../../studymatch-ui';
import type { VerifiedAchievementData, VerifiedCertificateData } from '../../types/certificates';
import './verify.css';

// public, what a certificate's QR code opens, no login needed
export function VerifyCertificatePage() {
  const { t } = useTranslation();
  const { code = '' } = useParams();
  const [certificate, setCertificate] = useState<VerifiedCertificateData | null>(null);
  const [failure, setFailure] = useState<'notFound' | 'failed' | null>(null);
  const [attempt, setAttempt] = useState(0);

  // only an unknown code means the certificate isn't valid, anything else (the backend down) can be tried again
  useEffect(() => {
    verifyCertificate(code).then(setCertificate)
      .catch((error: unknown) => setFailure(isBackendError(error) && error.code === 'NOT_FOUND' ? 'notFound' : 'failed'));
  }, [code, attempt]);

  const retry = () => {
    setFailure(null);
    setAttempt(previous => previous + 1);
  };

  return (
    <AuthLayout centerBrand hideFooter topRight={<ThemeSwitcher />} headline={t('verify.title')} tagline={null}>
      {failure === 'notFound' && (
        <AuthStatus icon={ShieldX} title={t('verify.notFound')}>
          <p>{t('verify.notFoundHint')}</p>
          <a href="/">{t('verify.goToStudyMatch')}</a>
        </AuthStatus>
      )}
      {failure === 'failed' && (
        <AuthStatus icon={CircleAlert} title={t('common.unexpectedError')}>
          <Button variant="secondary" icon={RefreshCw} onClick={retry}>{t('profile.tryAgain')}</Button>
        </AuthStatus>
      )}
      {failure === null && certificate === null && <div className="verify__loading"><Spinner /></div>}
      {certificate && <VerifiedCertificate certificate={certificate} />}
    </AuthLayout>
  );
}

function VerifiedCertificate({ certificate: c }: { certificate: VerifiedCertificateData }) {
  const { t, i18n } = useTranslation();
  const date = (value: string) => new Date(value).toLocaleDateString(i18n.language, { dateStyle: 'long' });
  return (
    <div className="verify">
      <p className="verify__valid"><ShieldCheck size={18} strokeWidth={2.25} aria-hidden />{t(c.record ? 'verify.validRecord' : 'verify.valid')}</p>
      <AuthHeader title={c.studentName}
        subtitle={[t('verify.studentLine', { number: c.studentNumber }), c.degree].filter(Boolean).join(' · ')} />
      {c.issuedAt && <p className="verify__issued">{t('verify.issuedOn', { date: date(c.issuedAt) })}</p>}
      <ul className="verify__list">
        {c.achievements.map(a => <VerifiedAchievement key={a.achievement} achievement={a} date={date} />)}
      </ul>
    </div>
  );
}

function VerifiedAchievement({ achievement: a, date }: { achievement: VerifiedAchievementData; date: (value: string) => string }) {
  const { t, i18n } = useTranslation();
  const e = a.evidence;
  const figures = e && [
    t('verify.roomsHeld', { count: e.roomsHeld }),
    t('verify.studentsTutored', { count: e.studentsTutored }),
    e.averageRating !== null && e.subject !== null
      && t('verify.averageRating', { rating: e.averageRating.toLocaleString(i18n.language, { maximumFractionDigits: 1 }), subject: e.subject }),
  ].filter(Boolean).join(' · ');
  return (
    <li className="verify__item">
      <AchievementBadge id={a.achievement} size={48} />
      <div>
        <p className="verify__name">{t(`achievements.${a.achievement}.name`)}</p>
        <p className="verify__meta">{t('achievements.earnedOn', { date: date(a.earnedAt) })}</p>
        {figures && <p className="verify__meta">{figures}</p>}
      </div>
    </li>
  );
}
