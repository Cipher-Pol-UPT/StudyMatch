import { Star } from 'lucide-react';
import { useRef,useState,type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import { useControllableState } from '../../utils/shared';
import './rating.css';

export interface RatingProps {
  /** 0–max. Read-only ratings accept decimals (4.6 shows a partial star). */
  value?: number;
  defaultValue?: number;
  /** Makes the rating an input. */
  onValueChange?: (value: number) => void;
  max?: number;
  size?: number;
  readOnly?: boolean;
  /** Show "4.6" next to the stars. */
  showValue?: boolean;
  /** e.g. 23 → "(23)". */
  count?: number;
  /** Accessible name of the input. */
  label?: string;
  className?: string;
}



/** 1–5 stars (Rate Tutor). Interactive as a radio group, or read-only with partial stars. */
export function Rating({ value, defaultValue = 0, onValueChange, max = 5, size = 22, readOnly, showValue, count, label = i18n.t('ui.rating'), className }: RatingProps) {
  useTranslation();
  const WORDS = ['', i18n.t('ui.poor'), i18n.t('ui.fair'), i18n.t('ui.good'), i18n.t('ui.very_good'), i18n.t('ui.excellent')];
  const [current, setCurrent] = useControllableState(value, defaultValue, onValueChange);
  const [hover, setHover] = useState<number | null>(null);
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const interactive = !readOnly && !!onValueChange;
  const shown = hover ?? current;

  const meta = (showValue || count !== undefined) && (
    <span className="sm-rating__meta">
      {showValue && <b>{current.toFixed(1)}</b>}
      {count !== undefined && <span>({count})</span>}
    </span>
  );

  if (!interactive) {
    return (
      <span className={cx('sm-rating', className)} role="img" aria-label={i18n.t('ui.rating_value', {label,value:current.toFixed(1),max})}>
        {Array.from({ length: max }).map((_, i) => {
          const fill = Math.max(0, Math.min(1, current - i));
          return (
            <span key={i} className="sm-rating__star" style={{ width: size, height: size }} aria-hidden>
              <Star size={size} strokeWidth={0} className="sm-rating__empty" />
              <span className="sm-rating__fill" style={{ width: `${fill * 100}%` }}>
                <Star size={size} strokeWidth={0} />
              </span>
            </span>
          );
        })}
        {meta}
      </span>
    );
  }

  const onKey = (e: KeyboardEvent, i: number) => {
    let n = -1;
    if (e.key === 'ArrowRight' || e.key === 'ArrowUp') n = Math.min(max, i + 2);
    if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') n = Math.max(1, i);
    if (n > 0) {
      e.preventDefault();
      setCurrent(n);
      refs.current[n - 1]?.focus();
    }
  };

  return (
    <span className={cx('sm-rating', 'sm-rating--input', className)}>
      <span role="radiogroup" aria-label={label} style={{ display: 'inline-flex' }} onMouseLeave={() => setHover(null)}>
        {Array.from({ length: max }).map((_, i) => {
          const n = i + 1;
          const on = shown >= n;
          return (
            <button
              key={n}
              ref={(el) => {
                refs.current[i] = el;
              }}
              type="button"
              role="radio"
              aria-checked={current === n}
              aria-label={i18n.t('ui.rating_stars', {count:n,word: max===5 && WORDS[n] ? ', '+WORDS[n] : ''})}
              tabIndex={current === n || (current === 0 && n === 1) ? 0 : -1}
              className="sm-rating__btn"
              data-on={on}
              onMouseEnter={() => setHover(n)}
              onClick={() => setCurrent(n)}
              onKeyDown={(e) => onKey(e, i)}
            >
              <Star size={size} strokeWidth={0} />
            </button>
          );
        })}
      </span>
      {max === 5 && <span className="sm-rating__word" aria-live="polite">{WORDS[shown] ?? ''}</span>}
      {meta}
    </span>
  );
}
