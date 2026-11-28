// the four dates of an academic year's semesters (#334), as yyyy-mm-dd
export interface SemesterDatesData {
  semester1Start: string;
  semester1End: string;
  semester2Start: string;
  semester2End: string;
}

// startYear 2026 is 2026/2027; semesters is null for a year from before #334
export interface AcademicYearData {
  startYear: number;
  semesters: SemesterDatesData | null;
}

// a day, or a range, without classes (#336); lastDay is the same as firstDay for one day
export interface DayOffData {
  id: number;
  firstDay: string;
  lastDay: string;
  reason: string;
}

export interface DayOffRequest {
  firstDay: string;
  lastDay: string | null;
  reason: string;
}
