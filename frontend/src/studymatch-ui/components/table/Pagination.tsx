import { ChevronLeft,ChevronRight } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import { paginationRange } from './Pagination.shared';
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

export function Pagination({ page, pageCount, onPageChange, siblings = 1, info, className }: PaginationProps) {
  useTranslation();
  if (pageCount <= 1 && !info) return null;
  const range = paginationRange(page, Math.max(1, pageCount), siblings);
  return (
    <nav className={cx('sm-pagination', className)} aria-label={i18n.t('ui.pagination')}>
      {info && <span className="sm-pagination__info">{info}</span>}
      <button type="button" onClick={() => onPageChange(page - 1)} disabled={page <= 1} aria-label={i18n.t('ui.previous_page')}>
        <ChevronLeft size={18} strokeWidth={2.25} />
      </button>
      {range.map((p, i) =>
        p === 'gap' ? (
          <span key={`gap-${i}`} className="sm-pagination__gap" aria-hidden>
            …
          </span>
        ) : (
          <button key={p} type="button" onClick={() => onPageChange(p)} aria-current={p === page ? 'page' : undefined} aria-label={i18n.t('ui.page', {page:p})}>
            {p}
          </button>
        ),
      )}
      <button type="button" onClick={() => onPageChange(page + 1)} disabled={page >= pageCount} aria-label={i18n.t('ui.next_page')}>
        <ChevronRight size={18} strokeWidth={2.25} />
      </button>
    </nav>
  );
}
