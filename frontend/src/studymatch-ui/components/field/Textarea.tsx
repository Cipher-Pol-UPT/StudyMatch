import { forwardRef,useCallback,useId,useLayoutEffect,useRef,useState,type ChangeEvent,type ReactNode,type TextareaHTMLAttributes } from 'react';
import { cx } from '../../utils/cx';
import { Field } from './Field';
import { describedBy } from './Field.shared';

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: ReactNode;
  hint?: ReactNode;
  error?: ReactNode;
  optional?: boolean;
  autoResize?: boolean;
  showCount?: boolean;
  containerClassName?: string;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { label, hint, error, optional, autoResize = true, showCount, maxLength, id, containerClassName, className, onChange, value, defaultValue, rows = 3, disabled, ...rest },
  ref,
) {
  const auto = useId();
  const fid = id ?? auto;
  const inner = useRef<HTMLTextAreaElement | null>(null);
  const [length, setLength] = useState(String(value ?? defaultValue ?? '').length);

  const setRefs = useCallback(
    (el: HTMLTextAreaElement | null) => {
      inner.current = el;
      if (typeof ref === 'function') ref(el);
      else if (ref) ref.current = el;
    },
    [ref],
  );

  const fit = useCallback(() => {
    const el = inner.current;
    if (!el || !autoResize) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }, [autoResize]);

  useLayoutEffect(() => {
    fit();
  }, [value, fit]);

  const handleChange = (e: ChangeEvent<HTMLTextAreaElement>) => {
    setLength(e.target.value.length);
    fit();
    onChange?.(e);
  };

  const visibleLength = value !== undefined ? String(value).length : length;
  const counting = showCount ?? maxLength !== undefined;
  return (
    <Field
      id={fid}
      label={label}
      hint={hint}
      error={error}
      optional={optional}
      disabled={disabled}
      top
      className={containerClassName}
      aside={
        counting ? (
          <span className={cx('sm-field__count', maxLength !== undefined && length > maxLength && 'sm-field__count--over')} aria-live="polite">
            {visibleLength}
            {maxLength !== undefined && ` / ${maxLength}`}
          </span>
        ) : undefined
      }
    >
      <textarea
        ref={setRefs}
        id={fid}
        rows={rows}
        className={cx('sm-field__input', className)}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(fid, hint, error)}
        maxLength={maxLength}
        value={value}
        defaultValue={defaultValue}
        onChange={handleChange}
        disabled={disabled}
        style={autoResize ? { resize: 'none', overflow: 'hidden' } : undefined}
        {...rest}
      />
    </Field>
  );
});
