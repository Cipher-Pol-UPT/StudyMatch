import { motion } from 'framer-motion';
import './RevealText.css';

export interface RevealTextProps {
  text: string;
  delay?: number;
}

export function RevealText({ text, delay = 0 }: RevealTextProps) {
  const words = text.split(' ');

  return (
    <span className="reveal-text" aria-label={text}>
      {words.map((word, index) => (
        <span key={index} className="reveal-text__mask" aria-hidden>
          <motion.span
            className="reveal-text__word"
            initial={{ y: '110%' }}
            animate={{ y: '0%' }}
            transition={{ duration: 0.7, delay: delay + index * 0.08, ease: [0.22, 1, 0.36, 1] }}
          >
            {word}
          </motion.span>
        </span>
      ))}
    </span>
  );
}
