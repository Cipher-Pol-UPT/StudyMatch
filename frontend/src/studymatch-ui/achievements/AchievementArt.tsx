import { useTranslation } from 'react-i18next';
import './achievements.css';
import { ACHIEVEMENT_ART,type AchievementId } from './catalog';
export interface AchievementArtProps { id: AchievementId; size?: number; className?: string }
export function AchievementArt({ id, size = 96, className }: AchievementArtProps) {
  const { t } = useTranslation(); const art = ACHIEVEMENT_ART[id];
  return <span role="img" aria-label={t(`achievements.${id}.name`)} className={className}
    style={{ display:'inline-grid', placeItems:'center', width:size, height:size, borderRadius:20, background:art.tint, fontSize:size/2 }}>{art.symbol}</span>;
}
