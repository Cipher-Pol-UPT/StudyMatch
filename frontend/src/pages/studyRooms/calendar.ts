// real calendar helpers, shared by the StudyRooms calendar and mini-calendar

export const dateKey = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export const monthStart = (date: Date) => new Date(date.getFullYear(), date.getMonth(), 1);

export const shiftMonth = (date: Date, offset: number) => new Date(date.getFullYear(), date.getMonth() + offset, 1);

// six weeks starting on the Monday on or before the 1st, so the grid is always full
export function monthDays(month: Date) {
  const first = monthStart(month);
  const offset = (first.getDay() + 6) % 7;
  return Array.from({ length: 42 }, (_, index) => new Date(first.getFullYear(), first.getMonth(), 1 - offset + index));
}
