import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { TextField } from '../../studymatch-ui';

export interface GradeCellProps {
  value: number | undefined;
  // who and what, for screen readers, since the cell only shows the number
  label: string;
  // resolves to an error to show under the cell, null when it was cancelled, or nothing once saved
  onCommit: (value: number) => Promise<string | null | undefined>;
}

// the grade as text until clicked, then a field: Enter or leaving it saves, Escape cancels
export function GradeCell({ value, label, onCommit }: GradeCellProps) {
  const { t, i18n } = useTranslation();
  const [draft, setDraft] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const shown = value === undefined ? '—' : new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 2 }).format(value);

  const close = () => { setDraft(null); setError(undefined); };

  async function commit() {
    if (draft === null || busy) return;
    const text = draft.trim().replace(',', '.');
    const next = Number(text);
    if (text === '' || next === value) return close();
    if (!Number.isFinite(next) || next < 0 || next > 20) return setError(t('grades.range'));
    setBusy(true);
    const problem = await onCommit(Math.round(next * 100) / 100);
    setBusy(false);
    if (problem) setError(problem);
    else close();
  }

  if (draft === null) {
    return (
      <button type="button" className="grade-cell" aria-label={`${label}: ${shown}`} onClick={() => setDraft(value === undefined ? '' : String(value))}>
        {shown}
      </button>
    );
  }
  return (
    <TextField containerClassName="grade-cell__field" label={t('grades.grade')} aria-label={label} inputMode="decimal" autoFocus value={draft}
      disabled={busy} error={error} onChange={event => setDraft(event.target.value)} onBlur={() => void commit()}
      onKeyDown={event => {
        if (event.key === 'Enter') void commit();
        if (event.key === 'Escape') close();
      }} />
  );
}
