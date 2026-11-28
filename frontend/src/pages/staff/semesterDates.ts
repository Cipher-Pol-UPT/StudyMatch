import type { SemesterDatesData } from '../../types/academicYears';

export type DateField = keyof SemesterDatesData;
export const FIELDS: DateField[] = ['semester1Start', 'semester1End', 'semester2Start', 'semester2End'];

// an academic year runs from 1 September to 31 August
export const yearLimits = (startYear: number) => ({ from: `${startYear}-09-01`, to: `${startYear + 1}-08-31` });

// mid-September to January, then February to June; Staff adjusts them. The Start year confirmation (#67) starts here too
export const suggestedSemesters = (startYear: number): SemesterDatesData => ({
  semester1Start: `${startYear}-09-15`,
  semester1End: `${startYear + 1}-01-31`,
  semester2Start: `${startYear + 1}-02-15`,
  semester2End: `${startYear + 1}-06-30`,
});

// the backend's rules (AcademicYear.setSemesters), in its order, each under the date it's about. Checked here before
// sending, and the backend's answer lands in the same places
export function problemOf(dates: SemesterDatesData, startYear: number): { field: DateField; code: string; params?: Record<string, unknown> } | null {
  const { from, to } = yearLimits(startYear);
  const outside = FIELDS.find(field => dates[field] && (dates[field] < from || dates[field] > to));
  if (outside) return { field: outside, code: 'SEMESTER_OUTSIDE_YEAR', params: { from, to } };
  if (dates.semester1Start && dates.semester1End && dates.semester1End <= dates.semester1Start) {
    return { field: 'semester1End', code: 'SEMESTER_ENDS_BEFORE_START', params: { semester: 1 } };
  }
  if (dates.semester2Start && dates.semester2End && dates.semester2End <= dates.semester2Start) {
    return { field: 'semester2End', code: 'SEMESTER_ENDS_BEFORE_START', params: { semester: 2 } };
  }
  if (dates.semester1End && dates.semester2Start && dates.semester2Start <= dates.semester1End) {
    return { field: 'semester2Start', code: 'SEMESTERS_OVERLAP' };
  }
  return null;
}
