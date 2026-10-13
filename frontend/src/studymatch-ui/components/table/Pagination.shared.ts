import './table.css';

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
