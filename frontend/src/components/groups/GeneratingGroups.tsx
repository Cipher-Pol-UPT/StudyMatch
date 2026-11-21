import { Check } from 'lucide-react';
import { memo, useEffect, useRef, useState, type CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import { ProgressBar } from '../../studymatch-ui';
import type { GroupGenerationData } from '../../types/groups';
import './generatingGroups.css';

// what the matching weighs, in the order the page talks about it
const STEPS = ['personality', 'record', 'languages', 'balance'] as const;
// the four identity colours of the logo, one per student dot
const COLORS = ['var(--sm-lead)', 'var(--sm-tech)', 'var(--sm-creative)', 'var(--sm-exec)'];
// a few groups show what's happening, a class can have many more
const SHOWN_GROUPS = 3;
const QUEUE_COLUMNS = 12;
const SEAT_COLUMNS = 5;
// the bar takes a new value this often and glides there in a straight line (generatingGroups.css)
const TICK_MS = 200;
// once the groups are back the bar fills this fast, then the page shows them
export const FINISH_MS = 450;
// the bar's ceiling while waiting: only the server's answer fills it
const WAITING = 95;

interface GeneratingGroupsProps {
  // undefined while it loads, null when it couldn't: the bar then just waits for the groups
  plan?: GroupGenerationData | null;
  // the plan's round trip plus the search: how long the whole request should take
  expectedMs?: number;
  done: boolean;
  onFinished: () => void;
}

// how long since it started, in steps of ms, until it's stopped
function useElapsed(ms: number, stopped: boolean) {
  const [elapsed, setElapsed] = useState(0);
  // the start, taken once when the loader appears
  const [started] = useState(() => Date.now());
  useEffect(() => {
    if (stopped) return undefined;
    const timer = window.setInterval(() => setElapsed(Date.now() - started), ms);
    return () => window.clearInterval(timer);
  }, [ms, started, stopped]);
  return elapsed;
}

// the generating loader: the bar and the steps follow the server's real search time, and fill fast when it answers
export function GeneratingGroups({ plan, expectedMs, done, onFinished }: GeneratingGroupsProps) {
  const { t } = useTranslation();
  const elapsed = useElapsed(TICK_MS, done);
  const finished = useRef(onFinished);
  useEffect(() => { finished.current = onFinished; }, [onFinished]);
  useEffect(() => {
    if (!done) return undefined;
    const timer = window.setTimeout(() => finished.current(), FINISH_MS);
    return () => window.clearTimeout(timer);
  }, [done]);

  // the share of the expected time gone, never the end until the groups are back
  const fraction = done ? 1 : expectedMs === undefined ? 0 : Math.min(elapsed / Math.max(expectedMs, 1), 1) * 0.999;
  const step = done ? STEPS.length : Math.min(STEPS.length - 1, Math.floor(fraction * STEPS.length));
  const seconds = expectedMs === undefined ? undefined : Math.round(expectedMs / 1000);

  return (
    <div className={`generating-groups${done ? ' is-done' : ''}`}>
      <FormingGroups groups={plan?.groups} size={plan?.optimalSize} fraction={fraction} />
      <ProgressBar value={done ? 100 : fraction * WAITING} indeterminate={plan === null && !done} showLabel
        label={plan?.groups ? t('groups.generatingCount', { count: plan.groups, size: plan.optimalSize }) : t('groups.generating')}
        valueText={seconds === undefined ? undefined : seconds < 1 ? t('groups.generatingMoment') : t('groups.generatingTime', { count: seconds })} />
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

// the class waits in a row and the students take their seats as the time goes, one seat per student at the optimal size
const FormingGroups = memo(function FormingGroups({ groups, size, fraction }: { groups?: number; size?: number; fraction: number }) {
  const slots = Math.min(Math.max(groups ?? SHOWN_GROUPS, 1), SHOWN_GROUPS);
  const seats = size ?? 0;
  const students = slots * seats;
  // students sit down in turns, each group filling from the left
  const placed = Math.floor(fraction * students);
  const queueColumns = Math.min(Math.max(students, 1), QUEUE_COLUMNS);
  const seatColumns = Math.min(Math.max(seats, 1), SEAT_COLUMNS);
  const stage = {
    '--slots': slots,
    '--queue-rows': Math.max(1, Math.ceil(students / queueColumns)),
    '--seat-rows': Math.max(1, Math.ceil(seats / seatColumns)),
  } as CSSProperties;

  return (
    <div className="generating-groups__stage" style={stage} aria-hidden="true">
      {Array.from({ length: slots }, (_, slot) => (
        <div key={slot} className={`generating-groups__slot${seats > 0 && placed >= seats * slots - (slots - 1 - slot) ? ' is-full' : ''}`}
          style={{ '--slot': slot } as CSSProperties} />
      ))}
      {Array.from({ length: students }, (_, student) => {
        // dealt round the groups: student 0 to group 0, 1 to group 1, ... so they fill side by side
        const slot = student % slots;
        const seat = Math.floor(student / slots);
        return (
          <i key={student} className={`generating-groups__student${student < placed ? ' is-placed' : ''}`}
            style={{
              '--queue-x': (student % queueColumns) - (queueColumns - 1) / 2,
              '--queue-y': Math.floor(student / queueColumns),
              '--slot': slot,
              '--seat-x': (seat % seatColumns) - (Math.min(seats - Math.floor(seat / seatColumns) * seatColumns, seatColumns) - 1) / 2,
              '--seat-y': Math.floor(seat / seatColumns),
            } as CSSProperties}>
            <b style={{ background: COLORS[student % COLORS.length] }} />
          </i>
        );
      })}
    </div>
  );
});
