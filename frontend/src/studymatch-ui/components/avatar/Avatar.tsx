import { useState, type CSSProperties } from 'react';
import { cx } from '../../utils/cx';
import { hashString } from '../../utils/shared';
import { PROFILES, type ProfileKey } from '../badge/Badge';
import './avatar.css';

/** Two-stop gradients: colourful but calm, like iOS contact avatars. */
export const AVATAR_GRADIENTS = [
  'linear-gradient(135deg, #8f82ff, #4a3df5)',
  'linear-gradient(135deg, #6cc6f5, #2a7fd6)',
  'linear-gradient(135deg, #ffad7a, #eb6834)',
  'linear-gradient(135deg, #5fe0ad, #139e6c)',
  'linear-gradient(135deg, #ff8fbf, #d4498b)',
  'linear-gradient(135deg, #b5a6ff, #7a4cf0)',
  'linear-gradient(135deg, #4b4b5a, #111118)',
];

export function initialsOf(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

export interface AvatarProps {
  name: string;
  /** Photo URL. Falls back to initials if it fails to load. */
  src?: string;
  size?: number;
  /** Override the generated gradient. */
  color?: string;
  /** Small profile badge in the corner. */
  profile?: ProfileKey;
  presence?: 'online' | 'away' | 'offline';
  shape?: 'circle' | 'square';
  className?: string;
}

/** Initials on a deterministic gradient (same name, same colour), or a photo. */
export function Avatar({ name, src, size = 44, color, profile, presence, shape = 'circle', className }: AvatarProps) {
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
  /** How many avatars to show before "+N". */
  max?: number;
  /** Ring colour between avatars; match the surface behind the stack. */
  ring?: string;
  className?: string;
}

export function AvatarStack({ names, size = 36, max = 4, ring, className }: AvatarStackProps) {
  const shown = names.slice(0, max);
  const rest = names.length - shown.length;
  return (
    <span
      className={cx('sm-avatar-stack', className)}
      role="group"
      aria-label={`${names.length} people: ${names.join(', ')}`}
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
