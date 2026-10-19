import { useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { CalendarTask } from './Types';

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
  onTaskClick?: (task: CalendarTask) => void;
}

export function TaskCalendar({ tasks, weeks = 6, showWeekends = false, locale, onTaskClick }: TaskCalendarProps) {
  const [offset, setOffset] = useState(0); 
  const cols = showWeekends ? 7 : 5;
  const today = new Date();
  const todayKey = dayKey(today);
  const start = addDays(mondayOf(today), offset * 7);

  const byDay = useMemo(() => {
    const map = new Map<string, CalendarTask[]>();
    for (const t of tasks) {
      const k = dayKey(toDate(t.date));
      map.set(k, [...(map.get(k) ?? []), t]);
    }
    return map;
  }, [tasks]);

  const rows = Array.from({ length: weeks }, (_, w) => Array.from({ length: cols }, (_, c) => addDays(start, w * 7 + c)));
  const first = rows[0][0];
  const last = rows[weeks - 1][cols - 1];
  const sameMonth = first.getMonth() === last.getMonth() && first.getFullYear() === last.getFullYear();
  const month = (d: Date, long: boolean) => new Intl.DateTimeFormat(locale, { month: long ? 'long' : 'short', year: 'numeric' }).format(d);
  const rangeLabel = sameMonth ? month(first, true) : `${month(first, false)} – ${month(last, false)}`;
  const weekday = new Intl.DateTimeFormat(locale, { weekday: 'short' });
  const dayLabel = new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long' });

  return (
    <div className="sh-cal">
      <div className="sh-cal__bar">
        <span className="sh-cal__range" aria-live="polite">{rangeLabel}</span>
        <div className="sh-cal__nav">
          <button onClick={() => setOffset((o) => o - 1)} aria-label="Previous week"><ChevronLeft size={18} /></button>
          <button onClick={() => setOffset(0)} disabled={offset === 0}>Today</button>
          <button onClick={() => setOffset((o) => o + 1)} aria-label="Next week"><ChevronRight size={18} /></button>
        </div>
      </div>

      <table className="sh-cal__table">
        <thead>
          <tr>{rows[0].map((d) => <th key={d.getDay()} scope="col">{weekday.format(d)}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={dayKey(row[0])}>
              {row.map((d) => {
                const k = dayKey(d);
                const list = byDay.get(k) ?? [];
                const extra = list.length - MAX_VISIBLE;
                return (
                  <td key={k} className={k === todayKey ? 'is-today' : undefined} aria-label={dayLabel.format(d)}>
                    <span className="sh-cal__num">
                      {d.getDate() === 1 ? `${d.getDate()} ${new Intl.DateTimeFormat(locale, { month: 'short' }).format(d)}` : d.getDate()}
                    </span>
                    {list.slice(0, MAX_VISIBLE).map((t) => {
                      const inner = (
                        <>
                          <i style={{ background: t.color ?? '#8e4ec6' }} aria-hidden />
                          <span>{t.title}</span>
                        </>
                      );
                      return onTaskClick ? (
                        <button key={t.id} className="sh-task" title={t.title} onClick={() => onTaskClick(t)}>{inner}</button>
                      ) : (
                        <span key={t.id} className="sh-task" title={t.title}>{inner}</span>
                      );
                    })}
                    {extra > 0 && <span className="sh-task sh-task--more">+{extra} more</span>}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}