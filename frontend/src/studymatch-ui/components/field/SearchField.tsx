import { Search,X } from 'lucide-react';
import { forwardRef,useImperativeHandle,useRef,type ChangeEvent,type InputHTMLAttributes,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import { useControllableState } from '../../utils/shared';
import './field.css';

export interface SearchFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size' | 'value' | 'defaultValue' | 'onChange'> {
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;
  /** Accessible name. Defaults to the placeholder. */
  label?: string;
  size?: 'sm' | 'md';
  /** A keyboard hint shown on the right while empty, e.g. "⌘K". */
  shortcut?: ReactNode;
}

/** Pill search input with a clear button. Escape clears it. */
export const SearchField = forwardRef<HTMLInputElement, SearchFieldProps>(function SearchField(
  { value, defaultValue = '', onValueChange, onChange, placeholder = i18n.t('ui.search'), label, size = 'md', shortcut, className, onKeyDown, ...rest },
  ref,
) {
  useTranslation();
  const [current, setCurrent] = useControllableState(value, defaultValue, onValueChange);
  const inner = useRef<HTMLInputElement>(null);
  useImperativeHandle(ref, () => inner.current as HTMLInputElement);
  return (
    <div className={cx('sm-search', size === 'sm' && 'sm-search--sm', className)} role="search">
      <Search size={size === 'sm' ? 16 : 18} strokeWidth={2.25} aria-hidden />
      <input
        ref={inner}
        type="search"
        value={current}
        placeholder={placeholder}
        aria-label={label ?? placeholder}
        onChange={(e) => {
          setCurrent(e.target.value);
          onChange?.(e);
        }}
        onKeyDown={(e) => {
          if (e.key === 'Escape' && current) {
            e.preventDefault();
            setCurrent('');
          }
          onKeyDown?.(e);
        }}
        {...rest}
      />
      {current ? (
        <button
          type="button"
          className="sm-search__clear"
          aria-label={i18n.t('ui.clear_search')}
          onClick={() => {
            setCurrent('');
            inner.current?.focus();
          }}
        >
          <X size={12} strokeWidth={3} />
        </button>
      ) : (
        shortcut && <kbd aria-hidden>{shortcut}</kbd>
      )}
    </div>
  );
});
