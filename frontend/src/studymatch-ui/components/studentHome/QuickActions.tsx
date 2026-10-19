import { motion, useTransform, type MotionValue } from 'framer-motion';
import { BookOpen, ClipboardList, FolderOpen, GraduationCap, Users, CalendarDays } from 'lucide-react';
import { QuickActionCard, type QuickActionCardProps } from './QuickActionCard';

const ACTIONS: QuickActionCardProps[] = [
  { title: 'Study Rooms', subtitle: 'A shared space to focus, exchange ideas, and make progress together.', icon: <BookOpen size={64} />, color: '#9b5fd0', actionLabel: 'Join StudyRoom' },
  { title: 'My Subjects', subtitle: 'Explore your enrolled classes and dive into each subject.', icon: <FolderOpen size={64} />, color: '#2a9fd6', actionLabel: 'Check Subjects' },
  { title: 'My Study Groups', subtitle: 'Check out your matches and meet your study group.', icon: <Users size={64} />, color: '#ff6b6b', actionLabel: 'Check Groups' },
  { title: 'My Assignments', subtitle: 'See what is due and track what you have handed in.', icon: <ClipboardList size={64} />, color: '#1baf7a', actionLabel: 'View Assignments' },
  { title: 'My Grades', subtitle: 'Follow your averages and progress in every subject.', icon: <GraduationCap size={64} />, color: '#eb9a34', actionLabel: 'View Grades' },
  { title: 'Calendar', subtitle: 'Plan sessions around your deadlines and classes.', icon: <CalendarDays size={64} />, color: '#d6579b', actionLabel: 'Open Calendar' },
];
const VISIBLE = 3;

export function QuickActions({ progress, userName }: { progress: MotionValue<number>; userName: string }) {
  const welcomeX = useTransform(progress, [0.12, 0.24], ['-60vw', '0vw']);
  const welcomeO = useTransform(progress, [0.12, 0.22], [0, 1]);
  const cardsO = useTransform(progress, [0.16, 0.26], [0, 1]);
  const trackX = useTransform(progress, [0.28, 0.72], ['0%', `-${((ACTIONS.length - VISIBLE) / ACTIONS.length) * 100}%`]);
  const exitY = useTransform(progress, [0.72, 0.86], ['0vh', '-40vh']);
  const exitO = useTransform(progress, [0.72, 0.84], [1, 0]);
  const pe = useTransform(exitO, (o) => (o < 0.05 ? 'none' : 'auto'));
  const stageO = useTransform(progress, [0.1, 0.16], [0, 1]);

  return (
    <motion.section className="sh-qa-section" style={{ y: exitY, opacity: exitO, pointerEvents: pe }} aria-label="Quick actions">
      <motion.div style={{ opacity: stageO }} className="sh-qa-panel">
        <motion.h2 className="sh-welcome" style={{ x: welcomeX, opacity: welcomeO }}>
          <img className="sh-welcome__avatar" src="/avatar.png" alt="" width={72} height={72} />
          Welcome back, {userName}!
        </motion.h2>
        <hr className="sh-rule" />

        <motion.div className="sh-carousel" style={{ opacity: cardsO }}>
          <motion.ul className="sh-carousel__track" style={{ x: trackX, width: `${(ACTIONS.length / VISIBLE) * 100}%` }}>
            {ACTIONS.map((a) => (
              <li key={a.title} className="sh-carousel__item" style={{ width: `${100 / ACTIONS.length}%` }}>
                <QuickActionCard {...a} onAction={() => console.log(`Action: ${a.title}`)} />
              </li>
            ))}
          </motion.ul>
        </motion.div>
      </motion.div>
    </motion.section>
  );
}