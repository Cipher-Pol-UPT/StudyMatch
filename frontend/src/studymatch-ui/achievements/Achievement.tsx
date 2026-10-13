import type { CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../components/button/Button';
import { Card } from '../components/card/Card';
import { Dialog } from '../components/overlay/Dialog';
import { AchievementArt } from './AchievementArt';
import './achievements.css';
import type { AchievementId,AchievementState } from './catalog';
export interface AchievementBadgeProps { id: AchievementId; size?: number; onClick?: () => void }
export function AchievementBadge({ id, size, onClick }: AchievementBadgeProps) {
  const { t } = useTranslation(); const art = <AchievementArt id={id} size={size} />;
  return onClick ? <button type="button" className="sm-ach-badge" onClick={onClick} aria-label={t(`achievements.${id}.name`)}>{art}</button> : art;
}
export interface AchievementTileProps { id: AchievementId; artSize?: number; style?: CSSProperties }
export function AchievementTile({ id, artSize, style }: AchievementTileProps) {
  const { t } = useTranslation(); return <div style={style}><AchievementArt id={id} size={artSize} /><h3>{t(`achievements.${id}.name`)}</h3></div>;
}
export interface AchievementCardProps extends AchievementState { onSelect?: () => void }
export function AchievementCard({ id, earnedAt, onSelect }: AchievementCardProps) {
  const { t, i18n } = useTranslation();
  return <Card title={t(`achievements.${id}.name`)} description={t(`achievements.${id}.description`)} onClick={onSelect}>
    <AchievementArt id={id} />{earnedAt && <time dateTime={earnedAt}>{new Date(earnedAt).toLocaleDateString(i18n.language)}</time>}
  </Card>;
}
export interface AchievementGridProps { achievements: AchievementState[]; onSelect?: (id: AchievementId) => void }
export function AchievementGrid({ achievements, onSelect }: AchievementGridProps) {
  return <div className="sm-ach-grid">{achievements.map(a => <AchievementCard key={a.id} {...a} onSelect={onSelect ? () => onSelect(a.id) : undefined} />)}</div>;
}
export interface AchievementDialogProps { id: AchievementId | null; onClose: () => void; earnedAt?: string; progress?: number; onExportCertificate?: (id: AchievementId) => void; locale?: string }
export function AchievementDialog({ id, onClose, earnedAt, onExportCertificate }: AchievementDialogProps) {
  const { t } = useTranslation(); return <Dialog open={!!id} onClose={onClose} title={id ? t(`achievements.${id}.name`) : ''}>
    {id && <AchievementCard id={id} earnedAt={earnedAt} />}
    {id && onExportCertificate && <Button onClick={() => onExportCertificate(id)}>{t('achievements.export')}</Button>}
  </Dialog>;
}
export interface AchievementCertificateProps { id: AchievementId; recipient: string; earnedAt: string; verificationCode: string }
export function AchievementCertificate({ id, recipient, earnedAt, verificationCode }: AchievementCertificateProps) {
  const { t } = useTranslation(); return <article><h2>{t('achievements.certificate')}</h2><p>{recipient}</p>
    <AchievementCard id={id} earnedAt={earnedAt} /><p>{verificationCode}</p></article>;
}
export interface AchievementUnlockedProps { id: AchievementId | null; onClose: () => void }
export function AchievementUnlocked(props: AchievementUnlockedProps) { return <AchievementDialog {...props} />; }
