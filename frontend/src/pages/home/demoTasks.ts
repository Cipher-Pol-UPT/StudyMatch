import type { CalendarTask } from './types';

const inDays = (n: number) => {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return d;
};

export const DEMO_TASKS: CalendarTask[] = [
  { id: '1', title: 'Math worksheet', date: inDays(1), color: 'var(--sm-danger)' },
  { id: '2', title: 'Essay draft', date: inDays(1), color: 'var(--sm-success)' },
  { id: '3', title: 'Lab report', date: inDays(2), color: 'var(--sm-tech)' },
  { id: '4', title: 'Group meeting', date: inDays(4), color: 'var(--sm-lead)' },
  { id: '5', title: 'Quiz', date: inDays(4), color: 'var(--sm-danger)' },
  { id: '6', title: 'Reading', date: inDays(4), color: 'var(--sm-creative)' },
  { id: '7', title: 'Project pitch', date: inDays(4), color: 'var(--sm-tech)' },
  { id: '8', title: 'Presentation', date: inDays(9), color: 'var(--sm-lead)' },
  { id: '9', title: 'Exam', date: inDays(15), color: 'var(--sm-warning)' },
];