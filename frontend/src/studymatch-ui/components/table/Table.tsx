import { ArrowDown,ArrowUp,ArrowUpDown } from 'lucide-react';
import { useMemo,useState,type CSSProperties,type KeyboardEvent,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import { Skeleton } from '../progress/Progress';
import './table.css';

export interface TableColumn<T> {
  key: string;
  header: ReactNode;
  render?: (row: T, index: number) => ReactNode;
  align?: 'left' | 'right' | 'center';
  sortable?: boolean;
  sortValue?: (row: T) => string | number | null | undefined;
  width?: CSSProperties['width'];
}

export type SortState = { key: string; direction: 'asc' | 'desc' } | null;

export interface TableProps<T> {
  columns: TableColumn<T>[];
  rows: T[];
  rowKey: (row: T, index: number) => string | number;
  onRowClick?: (row: T) => void;
  isRowSelected?: (row: T) => boolean;
  empty?: ReactNode;
  loading?: boolean;
  loadingRows?: number;
  caption?: ReactNode;
  dense?: boolean;
  striped?: boolean;
  plain?: boolean;
  maxHeight?: CSSProperties['maxHeight'];
  sort?: SortState;
  defaultSort?: SortState;
  onSortChange?: (sort: SortState) => void;
  label?: string;
  className?: string;
}

const alignClass = (a?: 'left' | 'right' | 'center') => (a === 'right' ? 'sm-table__num' : a === 'center' ? 'sm-table__center' : undefined);

function compare(a: unknown, b: unknown) {
  if (a == null && b == null) return 0;
  if (a == null) return 1;
  if (b == null) return -1;
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' });
}

export function Table<T>({
  columns,
  rows,
  rowKey,
  onRowClick,
  isRowSelected,
  empty,
  loading,
  loadingRows = 5,
  caption,
  dense,
  striped,
  plain,
  maxHeight,
  sort,
  defaultSort = null,
  onSortChange,
  label,
  className,
}: TableProps<T>) {
  useTranslation();
  const [innerSort, setInnerSort] = useState<SortState>(defaultSort);
  const active = sort !== undefined ? sort : innerSort;

  const sorted = useMemo(() => {
    if (!active || sort !== undefined) return rows;
    const col = columns.find((c) => c.key === active.key);
    if (!col) return rows;
    const get = col.sortValue ?? ((r: T) => (r as Record<string, unknown>)[col.key] as string | number);
    const out = [...rows].sort((a, b) => compare(get(a), get(b)));
    return active.direction === 'desc' ? out.reverse() : out;
  }, [rows, columns, active, sort]);

  const toggleSort = (key: string) => {
    const next: SortState = !active || active.key !== key ? { key, direction: 'asc' } : active.direction === 'asc' ? { key, direction: 'desc' } : null;
    if (sort === undefined) setInnerSort(next);
    onSortChange?.(next);
  };

  const onRowKey = (e: KeyboardEvent<HTMLTableRowElement>, row: T) => {
    if (onRowClick && (e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault();
      onRowClick(row);
    }
  };

  return (
    <div className={cx('sm-table-wrap', plain && 'sm-table-wrap--plain', className)} style={{ maxHeight }}>
      <table className={cx('sm-table', dense && 'sm-table--dense', striped && 'sm-table--striped')} aria-label={caption ? undefined : label} aria-busy={loading || undefined}>
        {caption && <caption>{caption}</caption>}
        <thead>
          <tr>
            {columns.map((c) => {
              const dir = active?.key === c.key ? active.direction : null;
              return (
                <th
                  key={c.key}
                  scope="col"
                  className={alignClass(c.align)}
                  style={{ width: c.width }}
                  aria-sort={c.sortable ? (dir === 'asc' ? 'ascending' : dir === 'desc' ? 'descending' : 'none') : undefined}
                >
                  {c.sortable ? (
                    <button type="button" className="sm-table__sort" onClick={() => toggleSort(c.key)}>
                      {c.header}
                      {dir === 'asc' ? <ArrowUp size={13} strokeWidth={2.5} aria-hidden /> : dir === 'desc' ? <ArrowDown size={13} strokeWidth={2.5} aria-hidden /> : <ArrowUpDown size={13} strokeWidth={2.25} aria-hidden />}
                    </button>
                  ) : (
                    c.header
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {loading
            ? Array.from({ length: loadingRows }).map((_, i) => (
                <tr key={`sk-${i}`}>
                  {columns.map((c, j) => (
                    <td key={c.key} className={alignClass(c.align)}>
                      <Skeleton w={j === 0 ? '70%' : '50%'} h={14} />
                    </td>
                  ))}
                </tr>
              ))
            : sorted.map((row, i) => (
                <tr
                  key={rowKey(row, i)}
                  data-clickable={!!onRowClick}
                  aria-selected={isRowSelected ? isRowSelected(row) : undefined}
                  tabIndex={onRowClick ? 0 : undefined}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                  onKeyDown={(e) => onRowKey(e, row)}
                >
                  {columns.map((c) => (
                    <td key={c.key} className={alignClass(c.align)}>
                      {c.render ? c.render(row, i) : String((row as Record<string, unknown>)[c.key] ?? '—')}
                    </td>
                  ))}
                </tr>
              ))}
          {!loading && rows.length === 0 && (
            <tr className="sm-table__empty">
              <td colSpan={columns.length}>{empty ?? <div style={{ padding: 32, textAlign: 'center', color: 'var(--sm-text-2)' }}> {i18n.t('ui.nothing_to_show_yet')} </div>}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
