export type TraitName = 'OPENNESS' | 'CONSCIENTIOUSNESS' | 'EXTRAVERSION' | 'AGREEABLENESS' | 'NEUROTICISM';

// what each filter offers for the year, whatever is selected
export interface AnalyticsFiltersData {
  academicYears: number[];
  subjectEditions: { id: number; subjectCode: string; subjectName: string; classSections: string[] }[];
  groups: { id: number; subjectEditionId: number; assignment: string; number: number }[];
}

// aggregated only, the backend never sends a statute type; academicYear is null before the first year exists
export interface AnalyticsData {
  academicYear: number | null; totalStudents: number; activeSubjects: number; averageAttendance: number | null;
  attendanceOverTime: { month: string; attendance: number }[];
  personalityDistribution: { trait: TraitName; students: number }[];
  groupBalance: { groupId: number; assignmentId: number; assignment: string; subjectCode: string; number: number; members: number; averageGrade: number | null }[];
  firstYearOnly: boolean;
  filters: AnalyticsFiltersData;
}
