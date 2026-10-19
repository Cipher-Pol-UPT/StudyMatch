import { ChevronLeft,ChevronRight } from 'lucide-react';
import { useMemo,useRef,useState,type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import './calendar.css';

export interface CalendarEvent {
  date: Date | string;
  color?: string;
  label?: string;
}

export interface CalendarProps {
  value?: Date | null;
  onValueChange?: (date: Date) => void;
  events?: CalendarEvent[];
  min?: Date;
  max?: Date;
  weekStartsOn?: 0 | 1;
  locale?: string;
  defaultMonth?: Date;
  className?: string;
}

const sameDay = (a: Date, b: Date) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
const addDays = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
const toDate = (d: Date | string) => (typeof d === 'string' ? new Date(d.length === 10 ? `${d}T00:00:00` : d) : d);

export function Calendar({ value, onValueChange, events = [], min, max, weekStartsOn = 1, locale = i18n.language, defaultMonth, className }: CalendarProps) {
  useTranslation();
  const today = startOfDay(new Date());
  const [month, setMonth] = useState(() => {
    const d = defaultMonth ?? value ?? today;
    return new Date(d.getFullYear(), d.getMonth(), 1);
  });
  const [focus, setFocus] = useState<Date>(() => startOfDay(value ?? today));
  const grid = useRef<HTMLDivElement>(null);

  const days = useMemo(() => {
    const first = new Date(month.getFullYear(), month.getMonth(), 1);
    const offset = (first.getDay() - weekStartsOn + 7) % 7;
    const start = addDays(first, -offset);
    return Array.from({ length: 42 }, (_, i) => addDays(start, i));
  }, [month, weekStartsOn]);

  const weekdays = useMemo(() => {
    const fmt = new Intl.DateTimeFormat(locale, { weekday: 'narrow' });
    const long = new Intl.DateTimeFormat(locale, { weekday: 'long' });
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(2024, 0, 7 + i + weekStartsOn); 
      return { short: fmt.format(d), long: long.format(d) };
    });
  }, [locale, weekStartsOn]);

  const eventsByDay = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>();
    events.forEach((e) => {
      const d = toDate(e.date);
      const k = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
      map.set(k, [...(map.get(k) ?? []), e]);
    });
    return map;
  }, [events]);

  const disabled = (d: Date) => (min && d < startOfDay(min)) || (max && d > startOfDay(max)) || false;
  const title = new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(month);
  const dayLabel = new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

  const moveFocus = (d: Date) => {
    setFocus(d);
    if (d.getMonth() !== month.getMonth() || d.getFullYear() !== month.getFullYear()) setMonth(new Date(d.getFullYear(), d.getMonth(), 1));
    requestAnimationFrame(() => grid.current?.querySelector<HTMLButtonElement>('[data-focus="true"]')?.focus());
  };

  const onKeyDown = (e: KeyboardEvent) => {
    const map: Record<string, () => Date> = {
      ArrowLeft: () => addDays(focus, -1),
      ArrowRight: () => addDays(focus, 1),
      ArrowUp: () => addDays(focus, -7),
      ArrowDown: () => addDays(focus, 7),
      Home: () => addDays(focus, -((focus.getDay() - weekStartsOn + 7) % 7)),
      End: () => addDays(focus, 6 - ((focus.getDay() - weekStartsOn + 7) % 7)),
      PageUp: () => new Date(focus.getFullYear(), focus.getMonth() - 1, focus.getDate()),
      PageDown: () => new Date(focus.getFullYear(), focus.getMonth() + 1, focus.getDate()),
    };
    if (map[e.key]) {
      e.preventDefault();
      moveFocus(map[e.key]());
    }
  };

  return (
    <div className={cx('sm-cal', className)}>
      <div className="sm-cal__head">
        <span className="sm-cal__title" aria-live="polite">
          {title}
        </span>
        <div className="sm-cal__nav">
          <button type="button" aria-label={i18n.t('ui.previous_month')} onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}>
            <ChevronLeft size={18} strokeWidth={2.25} />
          </button>
          <button type="button" aria-label={i18n.t('ui.next_month')} onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}>
            <ChevronRight size={18} strokeWidth={2.25} />
          </button>
        </div>
      </div>
      <div className="sm-cal__grid" role="grid" aria-label={title} ref={grid} onKeyDown={onKeyDown}>
        <div role="row" className="sm-cal__row">
          {weekdays.map((w) => (
            <span key={w.long} role="columnheader" className="sm-cal__wd" aria-label={w.long}>
              {w.short}
            </span>
          ))}
        </div>
        {Array.from({ length: 6 }).map((_, r) => (
          <div role="row" className="sm-cal__row" key={r}>
            {days.slice(r * 7, r * 7 + 7).map((d) => {
              const outside = d.getMonth() !== month.getMonth();
              const selected = !!value && sameDay(d, value);
              const isFocus = sameDay(d, focus) && !outside;
              const evs = eventsByDay.get(`${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`) ?? [];
              const off = disabled(d);
              return (
                <span role="gridcell" key={d.toISOString()} aria-selected={selected}>
                  <button
                    type="button"
                    className="sm-cal__day"
                    data-outside={outside}
                    data-today={sameDay(d, today)}
                    data-selected={selected}
                    data-focus={isFocus}
                    tabIndex={isFocus ? 0 : -1}
                    disabled={off}
                    aria-label={i18n.t('ui.calendar_day', {date: dayLabel.format(d),events: evs.length ? i18n.t('ui.events', {count:evs.length}) : ''})}
                    onClick={() => {
                      setFocus(d);
                      if (outside) setMonth(new Date(d.getFullYear(), d.getMonth(), 1));
                      onValueChange?.(d);
                    }}
                  >
                    {d.getDate()}
                    {evs.length > 0 && (
                      <span className="sm-cal__dots" aria-hidden>
                        {evs.slice(0, 3).map((ev, i) => (
                          <i key={i} style={{ background: selected ? '#fff' : (ev.color ?? 'var(--sm-violet)') }} />
                        ))}
                      </span>
                    )}
                  </button>
                </span>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}
