import { motion } from 'framer-motion';
import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react';
import { cx } from '../../utils/cx';
import { nextIndex, useControllableState } from '../../utils/shared';
import './segmented.css';

export interface TabItem<T extends string> {
  value: T;
  label: ReactNode;
  /** Small counter pill, e.g. pending items. */
  count?: number;
  disabled?: boolean;
}

export interface TabsProps<T extends string> {
  tabs: TabItem<T>[];
  value?: T;
  defaultValue?: T;
  onValueChange?: (value: T) => void;
  /** Accessible name of the tab list. */
  label?: string;
  /** Prefix for tab / panel ids. Use `tabPanelProps(idBase, value)` on your panels. */
  idBase?: string;
  className?: string;
}

/** Underlined tabs for switching views inside a page. Arrow keys move between tabs. */
export function Tabs<T extends string>({ tabs, value, defaultValue, onValueChange, label, idBase, className }: TabsProps<T>) {
  const auto = useId();
  const base = idBase ?? auto;
  const [current, setCurrent] = useControllableState<T>(value, defaultValue ?? tabs[0]?.value, onValueChange);
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  const onKeyDown = (e: KeyboardEvent, index: number) => {
    const n = nextIndex(e.key, index, tabs.length);
    if (n < 0 || tabs[n].disabled) return;
    e.preventDefault();
    setCurrent(tabs[n].value);
    refs.current[n]?.focus();
  };

  return (
    <div className={cx('sm-tabs', className)} role="tablist" aria-label={label}>
      {tabs.map((t, i) => {
        const selected = t.value === current;
        return (
          <button
            key={t.value}
            ref={(el) => {
              refs.current[i] = el;
            }}
            id={`${base}-tab-${t.value}`}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={`${base}-panel-${t.value}`}
            tabIndex={selected ? 0 : -1}
            disabled={t.disabled}
            onClick={() => setCurrent(t.value)}
            onKeyDown={(e) => onKeyDown(e, i)}
          >
            {t.label}
            {t.count !== undefined && <span className="sm-tabs__count">{t.count}</span>}
            {selected && <motion.i layoutId={`sm-tab-${base}`} className="sm-tabs__bar" transition={{ type: 'spring', stiffness: 520, damping: 38 }} />}
          </button>
        );
      })}
    </div>
  );
}

/** Props for the panel that belongs to a tab: `<div {...tabPanelProps('profile', 'about')}>`. */
export function tabPanelProps(idBase: string, value: string) {
  return { id: `${idBase}-panel-${value}`, role: 'tabpanel' as const, 'aria-labelledby': `${idBase}-tab-${value}`, tabIndex: 0 };
}
