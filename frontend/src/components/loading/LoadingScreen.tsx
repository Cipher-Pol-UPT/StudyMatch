import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { Mark } from '../../studymatch-ui';
import './LoadingScreen.css';

const QUARTER_TURN_SECONDS = 0.6;
const SMOOTH_EASE = [0.65, 0, 0.35, 1] as const;

export function LoadingScreen() {
  const { t } = useTranslation();

  return (
    <motion.div
      className="loading-screen"
      role="status"
      aria-label={t('ui.loading')}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, transition: { duration: 0.4 } }}
    >
      <motion.div
        className="loading-screen__mark"
        exit={{ scale: 0.85, transition: { duration: 0.4 } }}
      >
        <motion.span
          className="loading-screen__ripple"
          aria-hidden
          animate={{ scale: [0.9, 1.9], opacity: [0.45, 0] }}
          transition={{ duration: QUARTER_TURN_SECONDS * 2, repeat: Infinity, ease: 'easeOut' }}
        />
        <motion.span
          className="loading-screen__spin"
          animate={{ rotate: [0, 90, 180, 270, 360] }}
          transition={{ duration: QUARTER_TURN_SECONDS * 4, repeat: Infinity, ease: SMOOTH_EASE }}
        >
          <Mark size={64} />
        </motion.span>
      </motion.div>

      <motion.p
        className="loading-screen__wordmark"
        initial={{ opacity: 0, y: 8, letterSpacing: '0.2em' }}
        animate={{ opacity: 1, y: 0, letterSpacing: '-0.02em' }}
        transition={{ duration: 0.8, delay: 0.15, ease: [0.22, 1, 0.36, 1] }}
      >
        StudyMatch
      </motion.p>
    </motion.div>
  );
}
