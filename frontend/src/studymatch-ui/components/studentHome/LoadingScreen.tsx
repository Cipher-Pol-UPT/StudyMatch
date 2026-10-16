import { motion, useReducedMotion } from 'framer-motion';
import { HaloBackground } from './HaloBackground';

const DOTS = ['#8b5cf6', '#2a9fd6', '#1baf7a', '#eb6834']; 
const STEP = 36; 
const SLOTS = [
  { x: 0, y: 0 },
  { x: STEP, y: 0 },
  { x: STEP, y: STEP },
  { x: 0, y: STEP },
];

function DotsLogo() {
  const reduce = useReducedMotion();
  return (
    <div className="sh-dots" role="img" aria-label="Loading StudyMatch">
      {DOTS.map((color, i) => {
        const path = [0, 1, 2, 3, 4].map((k) => SLOTS[(i + k) % 4]);
        return (
          <motion.span
            key={color}
            className="sh-dots__dot"
            style={{ background: color }}
            initial={{ x: SLOTS[i].x, y: SLOTS[i].y }}
            animate={reduce ? undefined : { x: path.map((s) => s.x), y: path.map((s) => s.y) }}
            transition={{ duration: 3.2, repeat: Infinity, ease: 'easeInOut' }}
          />
        );
      })}
    </div>
  );
}

export function LoadingScreen() {
  const reduce = useReducedMotion();
  return (
    <motion.div
      className="sh-loading"
      role="status"
      aria-live="polite"
      exit={{ opacity: 0, transition: { duration: 0.5, delay: 0.25 } }}
    >
      <HaloBackground />
      <motion.div layoutId="sm-logo" className="sh-loading__logo" transition={{ type: 'spring', stiffness: 120, damping: 20 }}>
        <DotsLogo />
      </motion.div>
    </motion.div>
  );
}