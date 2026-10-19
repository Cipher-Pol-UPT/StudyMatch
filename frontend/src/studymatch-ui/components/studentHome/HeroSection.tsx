import { motion, useTransform, type MotionValue } from 'framer-motion';
import { Logo } from '../layout/logo';

export function HeroSection({ progress }: { progress: MotionValue<number> }) {
  const y = useTransform(progress, [0.1, 0.28], ['0vh', '-35vh']);
  const opacity = useTransform(progress, [0.1, 0.24], [1, 0]);
  const pe = useTransform(opacity, (o) => (o < 0.05 ? 'none' : 'auto'));

  return (
    <motion.section className="sh-hero" style={{ y, opacity, pointerEvents: pe }} aria-label="StudyMatch">
      <div className="sh-hero__row">
        <motion.div layoutId="sm-logo" className="sh-hero__logo" transition={{ type: 'spring', stiffness: 120, damping: 20 }}>
          <Logo className="sh-hero__logo-inner" />
        </motion.div>

        <div className="sh-hero__text">
          <div className="sh-clip">
            <motion.h1
              className="sm-display sh-hero__title"
              initial={{ y: '110%' }}
              animate={{ y: 0 }}
              transition={{ duration: 0.8, delay: 0.5, ease: [0.22, 1, 0.36, 1] }}
            >
              StudyMatch
            </motion.h1>
          </div>
          <div className="sh-clip">
            <motion.p
              className="sh-hero__tagline"
              initial={{ y: '110%' }}
              animate={{ y: 0 }}
              transition={{ duration: 0.8, delay: 0.65, ease: [0.22, 1, 0.36, 1] }}
            >
              Ace your goals!
            </motion.p>
          </div>
        </div>
      </div>

      <motion.div className="sh-hero__sub" initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.7, delay: 0.9 }}>
        <p>Study, connected.</p>
        <small>Good things happen when the right minds meet.</small>
      </motion.div>
    </motion.section>
  );
}