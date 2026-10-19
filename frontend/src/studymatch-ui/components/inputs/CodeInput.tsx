import { useRef,type ClipboardEvent,type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import { useControllableState } from '../../utils/shared';
import './inputs.css';

export interface CodeInputProps {
  length?: number;
  value?: string;
  defaultValue?: string;
  onValueChange?: (code: string) => void;
  onComplete?: (code: string) => void;
  alphanumeric?: boolean;
  label?: string;
  error?: boolean;
  disabled?: boolean;
  className?: string;
}

export function CodeInput({ length = 6, value, defaultValue = '', onValueChange, onComplete, alphanumeric, label = i18n.t('ui.verification_code'), error, disabled, className }: CodeInputProps) {
  useTranslation();
  const [code, setCode] = useControllableState(value, defaultValue, onValueChange);
  const refs = useRef<(HTMLInputElement | null)[]>([]);
  const allowed = alphanumeric ? /[a-z0-9]/i : /\d/;

  const commit = (next: string) => {
    const clean = next.slice(0, length);
    setCode(clean);
    if (clean.length === length) onComplete?.(clean);
  };

  const setAt = (i: number, ch: string) => {
    const chars = code.padEnd(length, ' ').split('');
    chars[i] = ch;
    commit(chars.join('').replace(/\s+$/, '').replace(/ /g, ''));
  };

  const onKey = (e: KeyboardEvent<HTMLInputElement>, i: number) => {
    if (e.key === 'Backspace') {
      e.preventDefault();
      if (code[i]) setAt(i, ' ');
      else if (i > 0) {
        setAt(i - 1, ' ');
        refs.current[i - 1]?.focus();
      }
    } else if (e.key === 'ArrowLeft' && i > 0) refs.current[i - 1]?.focus();
    else if (e.key === 'ArrowRight' && i < length - 1) refs.current[i + 1]?.focus();
  };

  const onPaste = (e: ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pasted = e.clipboardData
      .getData('text')
      .split('')
      .filter((c) => allowed.test(c))
      .join('')
      .slice(0, length);
    if (!pasted) return;
    commit(alphanumeric ? pasted.toUpperCase() : pasted);
    refs.current[Math.min(pasted.length, length - 1)]?.focus();
  };

  return (
    <div className={cx('sm-code', error && 'sm-code--error', className)} role="group" aria-label={label}>
      {Array.from({ length }).map((_, i) => (
        <input
          key={i}
          ref={(el) => {
            refs.current[i] = el;
          }}
          value={code[i] ?? ''}
          inputMode={alphanumeric ? 'text' : 'numeric'}
          autoComplete={i === 0 ? 'one-time-code' : 'off'}
          maxLength={1}
          disabled={disabled}
          aria-label={i18n.t('ui.code_character', {label,index:i+1,length})}
          aria-invalid={error || undefined}
          onChange={(e) => {
            const ch = e.target.value.slice(-1);
            if (!allowed.test(ch)) return;
            setAt(i, alphanumeric ? ch.toUpperCase() : ch);
            if (i < length - 1) refs.current[i + 1]?.focus();
          }}
          onKeyDown={(e) => onKey(e, i)}
          onPaste={onPaste}
          onFocus={(e) => e.target.select()}
        />
      ))}
    </div>
  );
}
