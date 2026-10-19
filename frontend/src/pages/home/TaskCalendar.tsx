import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../studymatch-ui';
import './home.css';
import type { CalendarTask } from './types';

const MAX_VISIBLE = 3;

const pad = (n: number) => String(n).padStart(2, '0');
const dayKey = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const toDate = (v: Date | string) =>
  typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) ? new Date(+v.slice(0, 4), +v.slice(5, 7) - 1, +v.slice(8, 10)) : new Date(v);
const addDays = (d: Date, n: number) => {
  const r = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  r.setDate(r.getDate() + n);
  return r;
};
const mondayOf = (d: Date) => addDays(d, -((d.getDay() + 6) % 7));

export interface TaskCalendarProps {
  tasks: CalendarTask[];
  weeks?: number;
  showWeekends?: boolean;
  locale?: string;
  today?: Date;
  onTaskClick?: (task: CalendarTask) => void;
}

export function TaskCalendar({ tasks, weeks = 4, showWeekends = false, locale, today = new Date(), onTaskClick }: TaskCalendarProps) {
  const { t, i18n } = useTranslation();
  const lang = locale ?? i18n.language;
  const [offset, setOffset] = useState(0); 
  const cols = showWeekends ? 7 : 5;
  const todayKey = dayKey(today);
  const start = addDays(mondayOf(today), offset * 7);

  const byDay = useMemo(() => {
    const map = new Map<string, CalendarTask[]>();
    for (const task of tasks) {
      const k = dayKey(toDate(task.date));
      map.set(k, [...(map.get(k) ?? []), task]);
    }
    return map;
  }, [tasks]);

  const rows = Array.from({ length: weeks }, (_, w) => Array.from({ length: cols }, (_, c) => addDays(start, w * 7 + c)));
  const first = rows[0][0];
  const last = rows[weeks - 1][cols - 1];
  const fmt = (o: Intl.DateTimeFormatOptions, d: Date) => new Intl.DateTimeFormat(lang, o).format(d);
  const sameMonth = first.getMonth() === last.getMonth() && first.getFullYear() === last.getFullYear();
  const short = { month: 'short', year: 'numeric' } as const;
  const rangeLabel = sameMonth ? fmt({ month: 'long', year: 'numeric' }, first) : `${fmt(short, first)} – ${fmt(short, last)}`;

  return (
    <div className="home-cal">
      <div className="home-cal__bar">
        <span className="home-cal__range" aria-live="polite">{rangeLabel}</span>
        <div className="home-cal__nav">
          <button type="button" className="home-cal__icon" aria-label={t('home.calendar.previousWeek')} onClick={() => setOffset((o) => o - 1)}>
            <ChevronLeft size={18} strokeWidth={2.25} />
          </button>
          <Button variant="secondary" disabled={offset === 0} onClick={() => setOffset(0)}>{t('home.calendar.today')}</Button>
          <button type="button" className="home-cal__icon" aria-label={t('home.calendar.nextWeek')} onClick={() => setOffset((o) => o + 1)}>
            <ChevronRight size={18} strokeWidth={2.25} />
          </button>
        </div>
      </div>

      <div className="home-cal__scroll">
        <table className="home-cal__table" aria-label={t('home.calendar.label')}>
          <thead>
            <tr>{rows[0].map((d) => <th key={d.getDay()} scope="col">{fmt({ weekday: 'short' }, d)}</th>)}</tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={dayKey(row[0])}>
                {row.map((d) => {
                  const k = dayKey(d);
                  const list = byDay.get(k) ?? [];
                  const extra = list.length - MAX_VISIBLE;
                  return (
                    <td key={k} className={k === todayKey ? 'is-today' : undefined}>
                      <span className="sm-visually-hidden">{fmt({ weekday: 'long', day: 'numeric', month: 'long' }, d)}</span>
                      <span className="home-cal__num" aria-hidden>
                        {d.getDate() === 1 ? `${d.getDate()} ${fmt({ month: 'short' }, d)}` : d.getDate()}
                      </span>
                      <div className="home-cal__tasks">
                        {list.slice(0, MAX_VISIBLE).map((task) => {
                          const inner = (
                            <>
                              <i className="home-task__bar" style={{ background: task.color ?? 'var(--sm-violet)' }} aria-hidden />
                              <span className="home-task__label">{task.title}</span>
                            </>
                          );
                          return onTaskClick ? (
                            <button key={task.id} type="button" className="home-task" title={task.title} onClick={() => onTaskClick(task)}>{inner}</button>
                          ) : (
                            <span key={task.id} className="home-task" title={task.title}>{inner}</span>
                          );
                        })}
                        {extra > 0 && <span className="home-task home-task--more">{t('home.calendar.more', { count: extra })}</span>}
                      </div>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}