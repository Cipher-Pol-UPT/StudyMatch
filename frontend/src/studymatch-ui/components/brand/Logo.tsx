import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { EASE_OUT } from '../../utils/shared';

export interface MarkProps {
  size?: number;
  /** Frame colour: black (default) or white on dark/violet surfaces. */
  bg?: string;
  /** The four profile dots, in fixed order: Leadership, Problem Solving, Creativity, Execution. */
  dots?: [string, string, string, string];
  /** Slow quarter-turns, for loading and hero moments. */
  animate?: boolean;
}

/** Four people, one team: the StudyMatch mark. */
export function Mark({ size = 40, bg = '#000', dots = ['#7a70ff', '#2a9fd6', '#eb6834', '#1baf7a'], animate }: MarkProps) {
  useTranslation();
  return (
    <motion.span
      role="img"
      aria-label={i18n.t('ui.studymatch')}
      style={{ width: size, height: size, borderRadius: size * 0.28, background: bg, display: 'inline-grid', gridTemplateColumns: '1fr 1fr', gap: size * 0.09, padding: size * 0.22, flexShrink: 0, boxSizing: 'border-box' }}
      animate={animate ? { rotate: [0, 0, 90, 90, 180, 180] } : undefined}
      transition={animate ? { duration: 6, repeat: Infinity, ease: EASE_OUT, times: [0, 0.3, 0.45, 0.65, 0.8, 1] } : undefined}
    >
      {dots.map((c, i) => (
        <i key={i} style={{ borderRadius: '50%', background: c, display: 'block' }} />
      ))}
    </motion.span>
  );
}

export interface LogoProps {
  size?: number;
  /** White wordmark and frame for dark surfaces. */
  inverted?: boolean;
  /** Hide the wordmark (mark only). */
  markOnly?: boolean;
  animate?: boolean;
}

export function Logo({ size = 32, inverted, markOnly, animate }: LogoProps) {
  useTranslation();
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: size * 0.32, fontFamily: 'var(--sm-font)', color: inverted ? '#fff' : '#000' }}>
      <Mark size={size} bg={inverted ? '#fff' : '#000'} animate={animate} />
      {!markOnly && <span style={{ fontSize: size * 0.56, fontWeight: 500, letterSpacing: '-0.025em', lineHeight: 1 }}>StudyMatch</span>}
    </span>
  );
}
