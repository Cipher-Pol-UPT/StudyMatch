import { motion, useReducedMotion } from 'framer-motion';

export function HaloBackground() {
  const reduce = useReducedMotion();
  return (
    <div className="sh-halo-bg" aria-hidden>
      <motion.div
        className="sh-halo sh-halo--a"
        animate={reduce ? undefined : { x: ['-8%', '10%', '-8%'], y: ['0%', '-6%', '0%'], rotate: [0, 8, 0] }}
        transition={{ duration: 9, repeat: Infinity, ease: 'easeInOut' }}
      />
      <motion.div
        className="sh-halo sh-halo--b"
        animate={reduce ? undefined : { x: ['10%', '-12%', '10%'], y: ['6%', '0%', '6%'] }}
        transition={{ duration: 11, repeat: Infinity, ease: 'easeInOut' }}
      />
    </div>
  );
}