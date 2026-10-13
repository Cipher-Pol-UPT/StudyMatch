import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { ReactNode } from 'react';
import { cx } from '../../utils/cx';
import './table.css';

export interface PaginationProps {
  /** 1-based current page. */
  page: number;
  pageCount: number;
  onPageChange: (page: number) => void;
  /** Pages shown on each side of the current one. */
  siblings?: number;
  /** Left-side summary, e.g. "Showing 21–40 of 132". */
  info?: ReactNode;
  className?: string;
}

/** Builds the list of pages with gaps: [1, '…', 4, 5, 6, '…', 12]. */
export function paginationRange(page: number, pageCount: number, siblings = 1): (number | 'gap')[] {
  const total = siblings * 2 + 5;
  if (pageCount <= total) return Array.from({ length: pageCount }, (_, i) => i + 1);
  const left = Math.max(page - siblings, 1);
  const right = Math.min(page + siblings, pageCount);
  const showLeftGap = left > 3;
  const showRightGap = right < pageCount - 2;
  if (!showLeftGap) return [...Array.from({ length: 3 + siblings * 2 }, (_, i) => i + 1), 'gap', pageCount];
  if (!showRightGap) return [1, 'gap', ...Array.from({ length: 3 + siblings * 2 }, (_, i) => pageCount - (3 + siblings * 2) + i + 1)];
  return [1, 'gap', ...Array.from({ length: right - left + 1 }, (_, i) => left + i), 'gap', pageCount];
}

export function Pagination({ page, pageCount, onPageChange, siblings = 1, info, className }: PaginationProps) {
  if (pageCount <= 1 && !info) return null;
  const range = paginationRange(page, Math.max(1, pageCount), siblings);
  return (
    <nav className={cx('sm-pagination', className)} aria-label="Pagination">
      {info && <span className="sm-pagination__info">{info}</span>}
      <button type="button" onClick={() => onPageChange(page - 1)} disabled={page <= 1} aria-label="Previous page">
        <ChevronLeft size={18} strokeWidth={2.25} />
      </button>
      {range.map((p, i) =>
        p === 'gap' ? (
          <span key={`gap-${i}`} className="sm-pagination__gap" aria-hidden>
            …
          </span>
        ) : (
          <button key={p} type="button" onClick={() => onPageChange(p)} aria-current={p === page ? 'page' : undefined} aria-label={`Page ${p}`}>
            {p}
          </button>
        ),
      )}
      <button type="button" onClick={() => onPageChange(page + 1)} disabled={page >= pageCount} aria-label="Next page">
        <ChevronRight size={18} strokeWidth={2.25} />
      </button>
    </nav>
  );
}
