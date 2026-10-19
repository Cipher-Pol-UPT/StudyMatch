import { AnimatePresence,motion } from 'framer-motion';
import { Delete } from 'lucide-react';
import { type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import { EASE_OUT } from '../../utils/shared';
import '../button/button.css';
import './expressive.css';

export interface AmountProps {
  value: string | number;
  prefix?: ReactNode;
  suffix?: ReactNode;
  color?: string;
  size?: number | string;
  className?: string;
}

export function Amount({ value, prefix, suffix, color, size, className }: AmountProps) {
  useTranslation();
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
  extraKey?: string;
  className?: string;
}

export function Keypad({ onKey, disabledKeys = [], extraKey, className }: KeypadProps) {
  useTranslation();
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', extraKey ?? '', '0', 'del'];
  return (
    <div className={cx('sm-keypad', className)} role="group" aria-label={i18n.t('ui.keypad')}>
      {keys.map((k, i) =>
        k === '' ? (
          <span key={`blank-${i}`} aria-hidden />
        ) : (
          <button key={k} type="button" onClick={() => onKey(k)} disabled={disabledKeys.includes(k)} aria-label={k === 'del' ? i18n.t('ui.delete') : k}>
            {k === 'del' ? <Delete size={26} strokeWidth={2} /> : k}
          </button>
        ),
      )}
    </div>
  );
}


export interface GroupSizePadProps {
  value: number; onValueChange: (value: number) => void; onSubmit?: () => void;
  title?: ReactNode; submitLabel?: string; caption?: ReactNode; disabledKeys?: string[];
}
export function GroupSizePad({ value, onValueChange, onSubmit, title, submitLabel, caption, disabledKeys }: GroupSizePadProps) {
  useTranslation();
  return <div className="sm-sizepad"><span>{title ?? i18n.t('ui.students_per_group')}</span>
    <Amount value={String(value)} /><div>{caption}</div>
    <Keypad disabledKeys={disabledKeys} onKey={key => onValueChange(key === 'del' ? 0 : Number(key))} />
    <button type="button" className="sm-btn" onClick={onSubmit}>{submitLabel ?? i18n.t('common.submit')}</button>
  </div>;
}
