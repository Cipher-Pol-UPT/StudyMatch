import { CheckCircle2,Download,Lock } from 'lucide-react';
import { useRef,type CSSProperties,type PointerEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../components/button/Button';
import { Dialog } from '../components/overlay/Dialog';
import { cx } from '../utils/cx';
import { AchievementArt } from './AchievementArt';
import './achievements.css';
import { ACHIEVEMENT_ART,RARITY,type AchievementId,type AchievementState } from './catalog';
export interface AchievementBadgeProps { id: AchievementId; size?: number; onClick?: () => void }
// round medallion like the library's: the ring shows the rarity, the art sits on a disc in its own tint
export function AchievementBadge({ id, size = 64, onClick }: AchievementBadgeProps) {
  const { t } = useTranslation();
  const { rarity, tint } = ACHIEVEMENT_ART[id];
  const style = { width: size, height: size, '--ring': RARITY[rarity].color, '--t1': tint[0], '--t2': tint[1] } as CSSProperties;
  const disc = <span className="sm-ach-badge__disc"><AchievementArt id={id} size={size * 0.86} animated={false} title="" /></span>;
  const label = t(`achievements.${id}.name`);
  return onClick
    ? <button type="button" className="sm-ach-badge" style={style} onClick={onClick} aria-label={label} title={label}>{disc}</button>
    : <span className="sm-ach-badge" style={style} role="img" aria-label={label} title={label}>{disc}</span>;
}
export function RarityPill({ id, inline }: { id: AchievementId; inline?: boolean }) {
  const { t } = useTranslation(); const { rarity } = ACHIEVEMENT_ART[id];
  return <span className={cx('sm-ach-rarity', inline && 'sm-ach-rarity--inline')} style={{ '--rarity': RARITY[rarity].color } as CSSProperties}>
    <i aria-hidden />{t(`achievements.rarity.${rarity}`)}
  </span>;
}
export interface AchievementTileProps { id: AchievementId; artSize?: number; showRarity?: boolean; locked?: boolean; style?: CSSProperties }
// the library's tinted stage with the 3D art, it tilts a little towards the mouse
export function AchievementTile({ id, artSize = 132, showRarity = true, locked, style }: AchievementTileProps) {
  const ref = useRef<HTMLDivElement>(null);
  const { rarity, tint } = ACHIEVEMENT_ART[id];
  const onMove = (event: PointerEvent<HTMLDivElement>) => {
    const el = ref.current;
    if (event.pointerType !== 'mouse' || !el) return;
    const box = el.getBoundingClientRect();
    const x = (event.clientX - box.left) / box.width, y = (event.clientY - box.top) / box.height;
    el.dataset.tilting = 'true';
    el.style.setProperty('--ry', `${(x - 0.5) * 16}deg`); el.style.setProperty('--rx', `${(0.5 - y) * 14}deg`);
    el.style.setProperty('--gx', `${x * 100}%`); el.style.setProperty('--gy', `${y * 100}%`);
  };
  const onLeave = () => {
    const el = ref.current;
    if (!el) return;
    el.dataset.tilting = 'false'; el.style.setProperty('--rx', '0deg'); el.style.setProperty('--ry', '0deg');
  };
  return <div ref={ref} className={cx('sm-ach-tile', `sm-ach-tile--${rarity}`, locked && 'sm-ach-tile--locked')} style={{ '--t1': tint[0], '--t2': tint[1], ...style } as CSSProperties}
    onPointerMove={onMove} onPointerLeave={onLeave}>
    {showRarity && <RarityPill id={id} />}
    <AchievementArt id={id} size={artSize} locked={locked} title="" />
    {locked && <span className="sm-ach-lock" aria-hidden><Lock size={14} strokeWidth={2.5} /></span>}
    <span className="sm-ach-tile__glare" aria-hidden />
  </div>;
}
function formatEarned(earnedAt: string, locale: string) {
  return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(earnedAt));
}

// without earnedAt it's still locked, and shows how to earn it instead of the date
export interface AchievementCardProps extends AchievementState { onSelect?: () => void }
export function AchievementCard({ id, earnedAt, onSelect }: AchievementCardProps) {
  const { t, i18n } = useTranslation();
  const Root = onSelect ? 'button' : 'div';
  return <Root type={onSelect ? 'button' : undefined} onClick={onSelect} className="sm-ach-card" data-state={earnedAt ? 'earned' : 'locked'}>
    <AchievementTile id={id} locked={!earnedAt} />
    <span className="sm-ach-card__body">
      <span className="sm-ach-card__name">{t(`achievements.${id}.name`)}</span>
      <span className="sm-ach-card__desc">{t(`achievements.${id}.description`)}</span>
      <span className="sm-ach-card__foot">
        {earnedAt ? (
          <span className="sm-ach-card__earned">
            <CheckCircle2 size={16} strokeWidth={2.5} aria-hidden />
            <time dateTime={earnedAt}>{t('achievements.earnedOn', { date: formatEarned(earnedAt, i18n.language) })}</time>
          </span>
        ) : (
          <span className="sm-ach-card__criteria"><Lock size={14} strokeWidth={2.5} aria-hidden />{t(`achievements.${id}.criteria`)}</span>
        )}
      </span>
    </span>
  </Root>;
}
export interface AchievementGridProps { achievements: AchievementState[]; onSelect?: (id: AchievementId) => void }
export function AchievementGrid({ achievements, onSelect }: AchievementGridProps) {
  return <div className="sm-ach-grid">{achievements.map(a => <AchievementCard key={a.id} {...a} onSelect={onSelect ? () => onSelect(a.id) : undefined} />)}</div>;
}
export interface AchievementDialogProps { id: AchievementId | null; onClose: () => void; earnedAt?: string; progress?: number; onExportCertificate?: (id: AchievementId) => void; locale?: string }
export function AchievementDialog({ id, onClose, earnedAt, onExportCertificate, locale }: AchievementDialogProps) {
  const { t, i18n } = useTranslation();
  const earned = earnedAt && formatEarned(earnedAt, locale ?? i18n.language);
  return <Dialog open={!!id} onClose={onClose} size="sm" title={<span className="sm-visually-hidden">{id ? t(`achievements.${id}.name`) : ''}</span>}
    footer={id && <>
      <Button variant="secondary" onClick={onClose}>{t('ui.close')}</Button>
      {earnedAt && onExportCertificate && <Button icon={Download} onClick={() => onExportCertificate(id)}>{t('achievements.export')}</Button>}
    </>}>
    {id && <div className="sm-ach-detail">
      <AchievementTile id={id} artSize={190} showRarity={false} locked={!earnedAt} />
      <RarityPill id={id} inline />
      <h3 className="sm-ach-detail__name">{t(`achievements.${id}.name`)}</h3>
      <p className="sm-ach-detail__desc">{t(`achievements.${id}.description`)}</p>
      <p className="sm-ach-detail__how"><b>{t('achievements.howToEarn')}</b>{t(`achievements.${id}.criteria`)}</p>
      {earned &&<span className="sm-ach-card__earned"><CheckCircle2 size={16} strokeWidth={2.5} aria-hidden />{t('achievements.earnedOn', { date: earned })}</span>}
    </div>}
  </Dialog>;
}
export interface AchievementUnlockedProps { id: AchievementId | null; onClose: () => void }
export function AchievementUnlocked(props: AchievementUnlockedProps) { return <AchievementDialog {...props} />; }
