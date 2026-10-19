import { motion, useTransform, type MotionValue } from 'framer-motion';
import { StatCard } from '../card/Card';
import { TaskCalendar } from './TaskCalendar';
import type { AgendaStats, CalendarTask } from './Types';

export interface AgendaSectionProps {
  progress: MotionValue<number>;
  tasks: CalendarTask[];
  stats: AgendaStats;
  locale?: string;
  onTaskClick?: (task: CalendarTask) => void;
}

export function AgendaSection({ progress, tasks, stats, locale, onTaskClick }: AgendaSectionProps) {
  const opacity = useTransform(progress, [0.76, 0.9], [0, 1]);
  const pe = useTransform(opacity, (o) => (o < 0.3 ? 'none' : 'auto'));

  return (
    <motion.section className="sh-agenda" style={{ opacity, pointerEvents: pe }} aria-label="Upcoming tasks">
      <div className="sh-agenda__panel">
        <h2 className="sh-agenda__title">Upcoming Tasks</h2>
        <TaskCalendar tasks={tasks} locale={locale} onTaskClick={onTaskClick} />
      </div>

      <aside className="sh-agenda__stats" aria-label="Your stats">
        <StatCard label="Attendance" value={stats.attendance} unit="%" />
        <StatCard label="Average" value={stats.average} />
        <StatCard label="Achievements" value={stats.achievements.earned} unit={`/${stats.achievements.total}`} />
      </aside>
    </motion.section>
  );
}