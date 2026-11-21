import { motion, useReducedMotion } from 'framer-motion';
import { Check } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ProgressBar, SPRING } from '../../studymatch-ui';
import './generatingGroups.css';

// what the matching weighs, in the order the page talks about it
const STEPS = ['personality', 'record', 'languages', 'balance'] as const;
// the four identity colours of the logo, one per student dot
const COLORS = ['var(--sm-lead)', 'var(--sm-tech)', 'var(--sm-creative)', 'var(--sm-exec)'];
const STUDENTS = 12;
const SLOTS = 3;
// a class takes about ten seconds; the bar slows down as it nears the end and only finishes when the groups arrive
const EXPECTED_MS = 10_000;
const TICK_MS = 200;
const SHUFFLE_MS = 1400;

// a different split every round: the students in a seeded random order, dealt into the groups in turn
function shuffled(round: number) {
  const order = Array.from({ length: STUDENTS }, (_, student) => student);
  let seed = round + 1;
  for (let i = order.length - 1; i > 0; i--) {
    seed = (seed * 16807) % 2147483647;
    const j = seed % (i + 1);
    [order[i], order[j]] = [order[j], order[i]];
  }
  const slots = new Array<number>(STUDENTS);
  order.forEach((student, position) => { slots[student] = position % SLOTS; });
  return slots;
}

export function GeneratingGroups() {
  const { t } = useTranslation();
  const reduce = useReducedMotion();
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    const started = Date.now();
    const timer = window.setInterval(() => setElapsed(Date.now() - started), TICK_MS);
    return () => window.clearInterval(timer);
  }, []);

  // 0 to 95%, about 90% when the ten seconds are up
  const progress = 95 * (1 - Math.exp(-elapsed / (EXPECTED_MS / 2.5)));
  const step = Math.min(STEPS.length - 1, Math.floor((elapsed / EXPECTED_MS) * STEPS.length));
  const slots = shuffled(reduce ? 0 : Math.floor(elapsed / SHUFFLE_MS));

  return (
    <div className="generating-groups">
      <div className="generating-groups__slots" aria-hidden="true">
        {Array.from({ length: SLOTS }, (_, slot) => (
          <div key={slot} className="generating-groups__slot">
            {slots.map((at, student) => at === slot && (
              <motion.i key={student} layoutId={`student-${student}`} transition={SPRING}
                className="generating-groups__dot" style={{ background: COLORS[student % COLORS.length] }} />
            ))}
          </div>
        ))}
      </div>
      <ProgressBar value={progress} showLabel label={t('groups.generating')} valueText={t('groups.generatingTime')} />
      <ol className="generating-groups__steps">
        {STEPS.map((name, index) => (
          <li key={name} className={index < step ? 'is-done' : index === step ? 'is-current' : undefined}
            aria-current={index === step ? 'step' : undefined}>
            <span className="generating-groups__mark">{index < step ? <Check size={12} aria-hidden="true" /> : index + 1}</span>
            {t(`groups.steps.${name}`)}
          </li>
        ))}
      </ol>
    </div>
  );
}
