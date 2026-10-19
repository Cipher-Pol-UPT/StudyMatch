import { motion } from 'framer-motion';
import { useId,useRef,type KeyboardEvent,type ReactNode } from 'react';
import { cx } from '../../utils/cx';
import { nextIndex,SPRING,useControllableState,type IconComponent } from '../../utils/shared';
import './segmented.css';

export interface SegmentedOption<T extends string | number> {
  value: T;
  label: ReactNode;
  icon?: IconComponent;
  disabled?: boolean;
}

export interface SegmentedProps<T extends string | number> {
  options: SegmentedOption<T>[];
  value?: T;
  defaultValue?: T;
  onValueChange?: (value: T) => void;
  label: string;
  size?: 'sm' | 'md';
  dark?: boolean;
  block?: boolean;
  className?: string;
}

export function Segmented<T extends string | number>({ options, value, defaultValue, onValueChange, label, size = 'md', dark, block, className }: SegmentedProps<T>) {
  const id = useId();
  const [current, setCurrent] = useControllableState<T>(value, defaultValue ?? options[0]?.value, onValueChange);
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  const onKeyDown = (e: KeyboardEvent, index: number) => {
    const n = nextIndex(e.key, index, options.length);
    if (n < 0) return;
    e.preventDefault();
    let i = n;
    for (let step = 0; step < options.length && options[i].disabled; step++) i = nextIndex(e.key === 'ArrowLeft' ? 'ArrowLeft' : 'ArrowRight', i, options.length);
    setCurrent(options[i].value);
    refs.current[i]?.focus();
  };

  return (
    <div className={cx('sm-seg', size === 'sm' && 'sm-seg--sm', dark && 'sm-seg--dark', block && 'sm-seg--block', className)} role="radiogroup" aria-label={label}>
      {options.map((o, i) => {
        const selected = o.value === current;
        const Icon = o.icon;
        return (
          <button
            key={String(o.value)}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={selected ? 0 : -1}
            disabled={o.disabled}
            onClick={() => setCurrent(o.value)}
            onKeyDown={(e) => onKeyDown(e, i)}
          >
            {selected && <motion.i layoutId={`sm-seg-${id}`} className="sm-seg__thumb" transition={SPRING} />}
            {Icon && <Icon size={16} strokeWidth={2.25} aria-hidden />}
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
