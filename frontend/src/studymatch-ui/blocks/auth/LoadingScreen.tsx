import { MotionConfig, motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { Mark } from '../../components/brand/Logo';
import { EASE_OUT } from '../../utils/shared';
import './auth.css';

// shown while the app starts; render it inside AnimatePresence so it fades into the first page
export function LoadingScreen() {
  const { t } = useTranslation();
  return (
    <MotionConfig reducedMotion="user">
      <motion.div className="sm-loading" role="status" aria-label={t('ui.loading')}
        exit={{ opacity: 0 }} transition={{ duration: 0.4, ease: EASE_OUT }}>
        <Mark size={56} animate />
      </motion.div>
    </MotionConfig>
  );
}
