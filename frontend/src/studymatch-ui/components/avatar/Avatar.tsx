import { useState,type CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import { hashString } from '../../utils/shared';
import { type ProfileKey } from '../badge/Badge';
import { PROFILES } from '../badge/Badge.shared';
import './avatar.css';
import { AVATAR_GRADIENTS,initialsOf } from './Avatar.shared';

export interface AvatarProps {
  name: string;
  src?: string;
  size?: number;
  color?: string;
  profile?: ProfileKey;
  presence?: 'online' | 'away' | 'offline';
  shape?: 'circle' | 'square';
  className?: string;
}

export function Avatar({ name, src, size = 44, color, profile, presence, shape = 'circle', className }: AvatarProps) {
  useTranslation();
  const [failed, setFailed] = useState(false);
  const bg = color ?? AVATAR_GRADIENTS[hashString(name) % AVATAR_GRADIENTS.length];
  const P = profile ? PROFILES[profile] : null;
  const showImg = src && !failed;
  return (
    <span
      className={cx('sm-avatar', shape === 'square' && 'sm-avatar--square', className)}
      style={{ width: size, height: size, background: showImg ? 'var(--sm-fill)' : bg, fontSize: size * 0.38 }}
      role="img"
      aria-label={P ? `${name}, ${P.label}` : name}
    >
      {showImg ? <img src={src} alt="" onError={() => setFailed(true)} /> : <span aria-hidden>{initialsOf(name)}</span>}
      {P && (
        <span className="sm-avatar__badge" style={{ background: P.color }} aria-hidden>
          <P.icon size={Math.max(8, size * 0.2)} strokeWidth={3} />
        </span>
      )}
      {presence && !P && <span className={cx('sm-avatar__presence', presence !== 'online' && `sm-avatar__presence--${presence}`)} aria-hidden />}
    </span>
  );
}

export interface AvatarStackProps {
  names: string[];
  size?: number;
  max?: number;
  ring?: string;
  className?: string;
}

export function AvatarStack({ names, size = 36, max = 4, ring, className }: AvatarStackProps) {
  useTranslation();
  const shown = names.slice(0, max);
  const rest = names.length - shown.length;
  return (
    <span
      className={cx('sm-avatar-stack', className)}
      role="group"
      aria-label={i18n.t('ui.people', {count:names.length,names:names.join(', ')})}
      style={ring ? ({ ['--sm-stack-ring' as string]: ring } as CSSProperties) : undefined}
    >
      {shown.map((n, i) => (
        <Avatar key={`${n}-${i}`} name={n} size={size} />
      ))}
      {rest > 0 && (
        <span className="sm-avatar sm-avatar--more" style={{ width: size, height: size, fontSize: size * 0.34 }} aria-hidden>
          +{rest}
        </span>
      )}
    </span>
  );
}
