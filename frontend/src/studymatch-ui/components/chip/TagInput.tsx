import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { cx } from '../../utils/cx';
import { useControllableState } from '../../utils/shared';
import { Chip } from './Chip';
import './chip.css';

export interface TagInputProps {
  label: ReactNode;
  value?: string[];
  defaultValue?: string[];
  onValueChange?: (tags: string[]) => void;
  placeholder?: string;
  /** Maximum number of tags. */
  max?: number;
  /** Quick-add suggestions shown under the field (already-added ones are hidden). */
  suggestions?: string[];
  hint?: ReactNode;
  className?: string;
}

/**
 * Free-form tags (skills, interests, languages).
 * Enter or comma adds a tag, Backspace on an empty input removes the last one.
 */
export function TagInput({ label, value, defaultValue = [], onValueChange, placeholder = 'Type and press Enter', max, suggestions = [], hint, className }: TagInputProps) {
  const id = useId();
  const [tags, setTags] = useControllableState(value, defaultValue, onValueChange);
  const [draft, setDraft] = useState('');
  const input = useRef<HTMLInputElement>(null);
  const full = max !== undefined && tags.length >= max;

  const add = (raw: string) => {
    const t = raw.trim().replace(/,$/, '');
    if (!t || full || tags.some((x) => x.toLowerCase() === t.toLowerCase())) return;
    setTags([...tags, t]);
    setDraft('');
  };
  const remove = (t: string) => setTags(tags.filter((x) => x !== t));

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      add(draft);
    } else if (e.key === 'Backspace' && !draft && tags.length) {
      remove(tags[tags.length - 1]);
    }
  };

  const left = suggestions.filter((s) => !tags.some((t) => t.toLowerCase() === s.toLowerCase()));
  return (
    <div className={cx('sm-field', className)}>
      <div className="sm-tags" onClick={() => input.current?.focus()}>
        <label className="sm-tags__label" htmlFor={id}>
          {label}
          {max !== undefined && ` · ${tags.length}/${max}`}
        </label>
        {tags.map((t) => (
          <Chip key={t} size="sm" onRemove={() => remove(t)} removeLabel={`Remove ${t}`}>
            {t}
          </Chip>
        ))}
        <input
          ref={input}
          id={id}
          value={draft}
          disabled={full}
          placeholder={full ? `Maximum of ${max}` : tags.length ? '' : placeholder}
          onChange={(e) => (e.target.value.endsWith(',') ? add(e.target.value) : setDraft(e.target.value))}
          onKeyDown={onKeyDown}
          onBlur={() => draft && add(draft)}
          aria-describedby={hint ? `${id}-hint` : undefined}
        />
      </div>
      {left.length > 0 && !full && (
        <div className="sm-tags__suggest" aria-label="Suggestions">
          {left.slice(0, 8).map((s) => (
            <button key={s} type="button" onClick={() => add(s)}>
              + {s}
            </button>
          ))}
        </div>
      )}
      {hint && (
        <div className="sm-field__hint" id={`${id}-hint`}>
          {hint}
        </div>
      )}
    </div>
  );
}
