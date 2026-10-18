import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, useScroll } from 'framer-motion';
import { MeshBackground } from '../studymatch-ui/components/studentHome/MeshBackground';
import { TopBar } from '../studymatch-ui/components/studentHome/Topbar';
import { HeroSection } from '../studymatch-ui/components/studentHome/HeroSection';
import { QuickActions } from '../studymatch-ui/components/studentHome/QuickActions';
import { AgendaSection } from '../studymatch-ui/components/studentHome/AgendaSection';
import '../studymatch-ui/components/layout/landing.css';
import '../studymatch-ui/components/studentHome/StudentHome.css';
import '../studymatch-ui/components/studentHome/studentHome.css';
import { DEMO_STATS, DEMO_TASKS } from '../studymatch-ui/components/studentHome/DemoData';
import type { AgendaStats, CalendarTask } from '../studymatch-ui/components/studentHome/Types';

export type Phase = 'loading' | 'ready';

const MIN_LOADING_MS = 2200;

export interface StudentHomeProps {
  userName?: string
  tasks?: CalendarTask[];
  stats?: AgendaStats;
  locale?: string;
  onTaskClick?: (task: CalendarTask) => void;
}

export function StudentHome({ userName = 'user', tasks = DEMO_TASKS, stats = DEMO_STATS, locale, onTaskClick }: StudentHomeProps) {
  const [phase, setPhase] = useState<Phase>('loading');
  const trackRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let alive = true;
    Promise.all([
      new Promise((r) => setTimeout(r, MIN_LOADING_MS)),
      document.fonts?.ready ?? Promise.resolve(),
    ]).then(() => alive && setPhase('ready'));
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    document.body.style.overflow = phase === 'loading' ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [phase]);

  const { scrollYProgress } = useScroll({ target: trackRef, offset: ['start start', 'end end'] });

  return (
    <div className="sm-root sh-root">
      <MeshBackground />

      {phase === 'ready' && <TopBar />}

      <div ref={trackRef} className="sh-track">
        <div className="sh-stage">
          {phase === 'ready' && (
            <>
              <HeroSection progress={scrollYProgress} />
              <QuickActions progress={scrollYProgress} userName={userName} />
              <AgendaSection progress={scrollYProgress} tasks={tasks} stats={stats} locale={locale} onTaskClick={onTaskClick} />
            </>
          )}
        </div>
      </div>
    </div>
  );
}