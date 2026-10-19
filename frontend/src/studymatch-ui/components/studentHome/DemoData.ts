import type { AgendaStats, CalendarTask } from './Types';

const inDays = (n: number) => {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return d;
};

export const DEMO_TASKS: CalendarTask[] = [
  { id: '1', title: 'Math worksheet', date: inDays(1), color: '#e5484d' },
  { id: '2', title: 'Essay draft', date: inDays(1), color: '#46a758' },
  { id: '3', title: 'Lab report', date: inDays(2), color: '#3eb0d5' },
  { id: '4', title: 'Group meeting', date: inDays(4), color: '#8e4ec6' },
  { id: '5', title: 'Quiz', date: inDays(4), color: '#e5484d' },
  { id: '6', title: 'Reading', date: inDays(4), color: '#f2a1d0' },
  { id: '7', title: 'Project pitch', date: inDays(4), color: '#3eb0d5' },
  { id: '8', title: 'Presentation', date: inDays(9), color: '#8e4ec6' },
  { id: '9', title: 'Exam', date: inDays(15), color: '#e5484d' },
];

export const DEMO_STATS: AgendaStats = { attendance: 90, average: 15.4, achievements: { earned: 7, total: 19 } };