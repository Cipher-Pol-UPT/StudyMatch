import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import { AvatarStack } from '../avatar/Avatar';
import { DomainStatus,ProfileDot,type ProfileKey } from '../badge/Badge';
import '../card/card.css';
import { ScoreRing } from '../progress/Progress';
import './expressive.css';

export interface GroupTileProps {
  name: string;
  members: string[];
  balance: number;
  profiles: ProfileKey[];
  status?: import('../../../types/enums').GroupStatus;
  selected?: boolean;
  onClick?: () => void;
}

export function GroupTile({ name, members, balance, profiles, status, selected, onClick }: GroupTileProps) {
  useTranslation();
  return (
    <motion.button
      type="button"
      onClick={onClick}
      whileHover={{ y: -4 }}
      whileTap={{ scale: 0.97 }}
      transition={{ type: 'spring', stiffness: 400, damping: 26 }}
      className={cx('sm-card', 'sm-group-tile', selected && 'sm-card--gradient sm-card--on-dark')}
      aria-pressed={selected}
      style={{ color: selected ? 'var(--sm-white)' : 'var(--sm-text)' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
        <div>
          <div className="sm-eyebrow" style={{ color: selected ? 'rgba(255,255,255,.72)' : undefined }}>
            {i18n.t('ui.student_count', {count:members.length})}
          </div>
          <div className="sm-group-tile__name">{name}</div>
          {status && (
            <div style={{ marginTop: 10 }}>
              <DomainStatus status={status} size="sm" />
            </div>
          )}
        </div>
        <ScoreRing value={balance} size={56} stroke={5} color={selected ? 'var(--sm-spark)' : 'var(--sm-violet)'} track={selected ? 'rgba(255,255,255,.25)' : 'var(--sm-fill)'} label={i18n.t('ui.balance')} />
      </div>
      <div className="sm-group-tile__foot">
        <AvatarStack names={members} size={34} ring={selected ? 'rgba(255,255,255,.35)' : undefined} />
        <span className="sm-group-tile__profiles" aria-label={i18n.t('ui.profiles_in_this_group')}>
          {profiles.map((p, i) => (
            <ProfileDot key={i} profile={p} ring={selected ? 'rgba(255,255,255,.6)' : 'var(--sm-surface)'} />
          ))}
        </span>
      </div>
    </motion.button>
  );
}

