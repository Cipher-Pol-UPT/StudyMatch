import { AnimatePresence, motion } from 'framer-motion';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { EASE_OUT } from '../../utils/shared';

export interface MarkProps {
  size?: number;
  bg?: string;
  dots?: [string, string, string, string];
  animate?: boolean;
}

export function Mark({ size = 40, bg = 'var(--sm-inverse)', dots = ['#7a70ff', '#2a9fd6', '#eb6834', '#1baf7a'], animate }: MarkProps) {
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

const SIGNATURE = 'barbosaz1';

function useSignature() {
  const [shown, setShown] = useState(false);
  useEffect(() => {
    let typed = '';
    let timer: number | undefined;
    const onKey = (event: KeyboardEvent) => {
      if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement || event.key.length !== 1) return;
      typed = (typed + event.key.toLowerCase()).slice(-SIGNATURE.length);
      if (typed !== SIGNATURE) return;
      typed = '';
      setShown(true);
      window.clearTimeout(timer);
      timer = window.setTimeout(() => setShown(false), 3200);
    };
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.clearTimeout(timer);
    };
  }, []);
  return shown;
}

export interface LogoProps {
  size?: number;
  inverted?: boolean;
  markOnly?: boolean;
  animate?: boolean;
}

export function Logo({ size = 32, inverted, markOnly, animate }: LogoProps) {
  useTranslation();
  const signed = useSignature();
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: size * 0.32, fontFamily: 'var(--sm-font)', color: inverted ? 'var(--sm-white)' : 'var(--sm-text)' }}>
      <motion.span style={{ display: 'inline-flex' }} animate={{ rotate: signed ? 360 : 0 }} transition={{ duration: signed ? 0.9 : 0, ease: EASE_OUT }}>
        <Mark size={size} bg={inverted ? 'var(--sm-white)' : 'var(--sm-inverse)'} animate={animate} />
      </motion.span>
      {!markOnly && <span style={{ fontSize: size * 0.56, fontWeight: 500, letterSpacing: '-0.025em', lineHeight: 1 }}>StudyMatch</span>}
      <AnimatePresence>
        {signed && (
          <motion.span aria-hidden initial={{ opacity: 0, x: -6 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0 }}
            style={{ font: `400 ${Math.max(10, size * 0.34)}px var(--sm-mono)`, color: inverted ? 'var(--sm-white)' : 'var(--sm-violet)' }}>
            {SIGNATURE}
          </motion.span>
        )}
      </AnimatePresence>
    </span>
  );
}
