import { AnimatePresence, motion } from 'framer-motion';
import { Delete } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { cx } from '../../utils/cx';
import { EASE_OUT } from '../../utils/shared';
import '../button/button.css';
import './expressive.css';

export interface AmountProps {
  value: string | number;
  prefix?: ReactNode;
  suffix?: ReactNode;
  color?: string;
  /** Font size; defaults to the responsive hero size. */
  size?: number | string;
  className?: string;
}

/** Big numeral where each digit slides up when it changes: the motion rule for numbers. */
export function Amount({ value, prefix, suffix, color, size, className }: AmountProps) {
  const chars = String(value).split('');
  return (
    <span className={cx('sm-amount', className)} style={{ color, fontSize: size }} aria-live="polite" aria-atomic>
      {prefix && <span className="sm-amount__prefix">{prefix}</span>}
      {chars.map((ch, i) => (
        <span key={i} className="sm-amount__slot">
          <AnimatePresence mode="popLayout" initial={false}>
            <motion.span
              key={`${ch}-${chars.length - i}`}
              style={{ display: 'inline-block' }}
              initial={{ y: '100%', opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              exit={{ y: '-100%', opacity: 0 }}
              transition={{ duration: 0.38, ease: EASE_OUT }}
            >
              {ch}
            </motion.span>
          </AnimatePresence>
        </span>
      ))}
      {suffix && <span className="sm-amount__suffix">{suffix}</span>}
    </span>
  );
}

export interface KeypadProps {
  onKey: (key: string) => void;
  disabledKeys?: string[];
  /** Key in the bottom-left slot (e.g. "." or "00"). Empty by default. */
  extraKey?: string;
  className?: string;
}

/** 3×4 numeric keypad. Sends "0"–"9", the extra key, and "del". Inherits the surface colour. */
export function Keypad({ onKey, disabledKeys = [], extraKey, className }: KeypadProps) {
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', extraKey ?? '', '0', 'del'];
  return (
    <div className={cx('sm-keypad', className)} role="group" aria-label="Keypad">
      {keys.map((k, i) =>
        k === '' ? (
          <span key={`blank-${i}`} aria-hidden />
        ) : (
          <button key={k} type="button" onClick={() => onKey(k)} disabled={disabledKeys.includes(k)} aria-label={k === 'del' ? 'Delete' : k}>
            {k === 'del' ? <Delete size={26} strokeWidth={2} /> : k}
          </button>
        ),
      )}
    </div>
  );
}

export interface GroupSizePadProps {
  value: number;
  onValueChange: (n: number) => void;
  min?: number;
  max?: number;
  /** Students in the class, used to preview the number of groups. */
  students: number;
  onSubmit?: () => void;
  submitLabel?: string;
  title?: ReactNode;
}

/**
 * Group size picker in the spirit of a payment keypad: one huge number,
 * a full-colour surface and a single action. Out-of-range keys shake.
 */
export function GroupSizePad({ value, onValueChange, min = 2, max = 6, students, onSubmit, submitLabel = 'Generate groups', title = 'Students per group' }: GroupSizePadProps) {
  const [shake, setShake] = useState(0);
  const groups = value ? Math.ceil(students / value) : 0;
  const press = (k: string) => {
    if (k === 'del') return onValueChange(0);
    const n = Number(k);
    if (n < min || n > max) {
      setShake((s) => s + 1);
      return;
    }
    onValueChange(n);
  };
  return (
    <div className="sm-sizepad">
      <span className="sm-eyebrow" style={{ color: 'rgba(255,255,255,.72)' }}>
        {title}
      </span>
      <div key={shake} className={shake ? 'sm-shake' : undefined} style={{ margin: '12px 0 4px' }}>
        <Amount value={String(value || 0)} />
      </div>
      <div className="sm-sizepad__caption sm-num" aria-live="polite">
        {value ? `${students} students → ${groups} groups` : `Choose ${min}–${max}`}
      </div>
      <Keypad onKey={press} className="sm-sizepad__keys" />
      <button type="button" className="sm-btn sm-btn--white sm-btn--lg sm-btn--block sm-sizepad__submit" disabled={!value} onClick={onSubmit}>
        <span className="sm-btn__label">{submitLabel}</span>
      </button>
    </div>
  );
}
