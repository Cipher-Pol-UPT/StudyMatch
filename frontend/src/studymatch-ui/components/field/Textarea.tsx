import { forwardRef, useCallback, useId, useLayoutEffect, useRef, useState, type ChangeEvent, type ReactNode, type TextareaHTMLAttributes } from 'react';
import { cx } from '../../utils/cx';
import { Field, describedBy } from './Field';

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: ReactNode;
  hint?: ReactNode;
  error?: ReactNode;
  optional?: boolean;
  /** Grow with the content instead of scrolling. */
  autoResize?: boolean;
  /** Show a live "120 / 500" counter. Defaults to true when `maxLength` is set. */
  showCount?: boolean;
  containerClassName?: string;
}

/** Multi-line field (e.g. the 500-character "About me"). */
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

  const fit = () => {
    const el = inner.current;
    if (!el || !autoResize) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  };

  useLayoutEffect(() => {
    if (value !== undefined) setLength(String(value).length);
    fit();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  const handleChange = (e: ChangeEvent<HTMLTextAreaElement>) => {
    setLength(e.target.value.length);
    fit();
    onChange?.(e);
  };

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
            {length}
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
